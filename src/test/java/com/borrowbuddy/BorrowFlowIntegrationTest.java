package com.borrowbuddy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BorrowFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completeBorrowingLifecycleWithQrHandoverAndReviews() throws Exception {
        String ownerToken = register("Owner Student", "owner@college.edu", "STU-OWN-1");
        String borrowerToken = register("Borrower Student", "borrower@college.edu", "STU-BOR-2");
        String thirdToken = register("Third Student", "third@college.edu", "STU-THI-3");

        // Unauthenticated protected endpoint
        MvcResult unauthorized = mockMvc.perform(get("/api/dashboard/stats")).andReturn();
        assertEquals(401, unauthorized.getResponse().getStatus());

        JsonNode createdItem = perform(post("/api/items"), ownerToken, 201, Map.of(
                "itemName", "Casio Scientific Calculator",
                "category", "Electronics",
                "description", "Exam approved calculator with slide cover.",
                "itemCondition", "GOOD",
                "availabilityStatus", "AVAILABLE",
                "pricePerDay", 25.50,
                "securityDeposit", 200
        ));
        long itemId = createdItem.at("/data/id").asLong();
        assertTrue(itemId > 0);
        assertEquals(25.50, createdItem.at("/data/pricePerDay").asDouble(), 0.001);
        assertEquals(200.00, createdItem.at("/data/securityDeposit").asDouble(), 0.001);

        // Owner cannot borrow their own item
        perform(post("/api/borrow-requests"), ownerToken, 400, Map.of(
                "itemId", itemId,
                "startDate", LocalDate.now().plusDays(1).toString(),
                "returnDate", LocalDate.now().plusDays(4).toString(),
                "message", "Mine"
        ));

        JsonNode borrowRequest = perform(post("/api/borrow-requests"), borrowerToken, 201, Map.of(
                "itemId", itemId,
                "startDate", LocalDate.now().plusDays(1).toString(),
                "returnDate", LocalDate.now().plusDays(5).toString(),
                "message", "I have an exam next week."
        ));
        long requestId = borrowRequest.at("/data/id").asLong();

        // Duplicate active request is blocked
        perform(post("/api/borrow-requests"), borrowerToken, 409, Map.of(
                "itemId", itemId,
                "startDate", LocalDate.now().plusDays(1).toString(),
                "returnDate", LocalDate.now().plusDays(5).toString()
        ));

        // A conflicting pending request from another student
        JsonNode conflicting = perform(post("/api/borrow-requests"), thirdToken, 201, Map.of(
                "itemId", itemId,
                "startDate", LocalDate.now().plusDays(2).toString(),
                "returnDate", LocalDate.now().plusDays(6).toString(),
                "message", "Also need it."
        ));
        long conflictingId = conflicting.at("/data/id").asLong();

        // Only the owner can approve
        perform(put("/api/borrow-requests/" + requestId + "/approve"), borrowerToken, 403, null);

        JsonNode approved = perform(put("/api/borrow-requests/" + requestId + "/approve"), ownerToken, 200, null);
        assertEquals("APPROVED", approved.at("/data/status").asText());
        long transactionId = approved.at("/data/transactionId").asLong();
        assertTrue(transactionId > 0);

        // Conflicting pending request is auto-rejected
        JsonNode thirdSent = perform(get("/api/borrow-requests/sent"), thirdToken, 200, null);
        String conflictingStatus = null;
        for (JsonNode node : thirdSent.at("/data")) {
            if (node.at("/id").asLong() == conflictingId) {
                conflictingStatus = node.at("/status").asText();
            }
        }
        assertEquals("REJECTED", conflictingStatus);
        perform(put("/api/borrow-requests/" + conflictingId + "/approve"), ownerToken, 409, null);

        // QR is owner-only and contains a single-use token
        perform(get("/api/transactions/" + transactionId + "/qr"), thirdToken, 403, null);
        JsonNode qr = perform(get("/api/transactions/" + transactionId + "/qr"), ownerToken, 200, null);
        String qrToken = qr.at("/data/token").asText();
        assertFalse(qrToken.isBlank());
        assertTrue(qr.at("/data/imageDataUrl").asText().startsWith("data:image/png;base64,"));

        // Borrower confirms handover by scanning the token
        JsonNode handedOver = perform(put("/api/transactions/" + transactionId + "/handover"),
                borrowerToken, 200, Map.of("token", qrToken));
        assertEquals("BORROWED", handedOver.at("/data/status").asText());
        assertNotNull(handedOver.at("/data/handoverDate").asText());

        // Reusing the same QR token is rejected
        perform(put("/api/transactions/" + transactionId + "/handover"),
                borrowerToken, 409, Map.of("token", qrToken));

        // Borrower returns the item
        JsonNode returned = perform(put("/api/transactions/" + transactionId + "/return"),
                borrowerToken, 200, null);
        assertEquals("COMPLETED", returned.at("/data/status").asText());
        assertTrue(returned.at("/data/canReview").asBoolean());

        // Both participants can review once
        JsonNode review = perform(post("/api/reviews"), borrowerToken, 201, Map.of(
                "transactionId", transactionId,
                "rating", 5,
                "comment", "Smooth handover and friendly owner."
        ));
        assertTrue(review.at("/data/id").asLong() > 0);
        perform(post("/api/reviews"), borrowerToken, 409, Map.of(
                "transactionId", transactionId,
                "rating", 4,
                "comment", "Duplicate"
        ));
        perform(post("/api/reviews"), ownerToken, 201, Map.of(
                "transactionId", transactionId,
                "rating", 5,
                "comment", "Returned on time."
        ));

        JsonNode borrowerProfile = perform(get("/api/students/profile"), borrowerToken, 200, null);
        long borrowerId = borrowerProfile.at("/data/user/id").asLong();
        JsonNode reviews = perform(get("/api/reviews/" + borrowerId), null, 200, null);
        assertEquals(1, reviews.at("/data/reviewCount").asLong());

        JsonNode ownerProfile = perform(get("/api/students/profile"), ownerToken, 200, null);
        assertEquals(1, ownerProfile.at("/data/completedLends").asLong());
        assertEquals(1, ownerProfile.at("/data/listedItems").asLong());
    }

    @Test
    void rejectsInvalidRegistrationAndDuplicateEmail() throws Exception {
        register("Validation Student", "validate@college.edu", "STU-VAL-9");

        perform(post("/api/auth/register"), null, 409, Map.of(
                "name", "Validation Student",
                "collegeEmail", "validate@college.edu",
                "password", "Password123",
                "confirmPassword", "Password123",
                "studentId", "STU-VAL-10"
        ));

        perform(post("/api/auth/register"), null, 400, Map.of(
                "name", "Outside Student",
                "collegeEmail", "outside@gmail.com",
                "password", "Password123",
                "confirmPassword", "Password123",
                "studentId", "STU-OUT-11"
        ));

        perform(post("/api/auth/register"), null, 400, Map.of(
                "name", "Weak Password",
                "collegeEmail", "weak@college.edu",
                "password", "password",
                "confirmPassword", "password",
                "studentId", "STU-WEAK-12"
        ));

        perform(post("/api/auth/login"), null, 401, Map.of(
                "collegeEmail", "validate@college.edu",
                "password", "WrongPassword1"
        ));
    }

    private String register(String name, String email, String studentId) throws Exception {
        JsonNode response = perform(post("/api/auth/register"), null, 201, Map.of(
                "name", name,
                "collegeEmail", email,
                "password", "Password123",
                "confirmPassword", "Password123",
                "studentId", studentId
        ));
        return response.at("/data/token").asText();
    }

    private JsonNode perform(MockHttpServletRequestBuilder builder, String token,
                             int expectedStatus, Object body) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        String content = result.getResponse().getContentAsString();
        assertEquals(expectedStatus, result.getResponse().getStatus(), content);
        if (content == null || content.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(content);
    }
}
