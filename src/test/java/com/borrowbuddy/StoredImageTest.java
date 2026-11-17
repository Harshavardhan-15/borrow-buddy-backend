package com.borrowbuddy;

import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoredImageTest {

    private static final String DEMO_PHOTO = "4b72a97d-bda2-478c-8cf5-5d81f554b7ee.jpg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageStorageService imageStorageService;

    @Test
    void storesImageInDatabaseAndServesItWithCachingHeaders() throws Exception {
        byte[] jpeg = fakeJpeg(4096);
        String url = imageStorageService.store(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg));

        assertTrue(url.startsWith("/uploads/"), url);
        assertTrue(url.endsWith(".jpg"), url);

        MvcResult response = mockMvc.perform(get(url)).andReturn();
        assertEquals(200, response.getResponse().getStatus());
        assertEquals("image/jpeg", response.getResponse().getContentType());
        assertArrayEquals(jpeg, response.getResponse().getContentAsByteArray());

        String etag = response.getResponse().getHeader("ETag");
        assertNotNull(etag);
        assertTrue(response.getResponse().getHeader("Cache-Control").contains("immutable"));

        MvcResult cached = mockMvc.perform(get(url).header("If-None-Match", etag)).andReturn();
        assertEquals(304, cached.getResponse().getStatus());
    }

    @Test
    void servesBundledDemoPhotoWhenTheDatabaseHasNoCopy() throws Exception {
        MvcResult response = mockMvc.perform(get("/uploads/" + DEMO_PHOTO)).andReturn();
        assertEquals(200, response.getResponse().getStatus());
        assertEquals("image/jpeg", response.getResponse().getContentType());
        assertTrue(response.getResponse().getContentAsByteArray().length > 1000);
    }

    @Test
    void returnsNotFoundForUnknownImage() throws Exception {
        MvcResult response = mockMvc.perform(get("/uploads/does-not-exist.jpg")).andReturn();
        assertEquals(404, response.getResponse().getStatus());
    }

    @Test
    void rejectsPathTraversalAndUnsafeNames() {
        assertTrue(imageStorageService.load("../pom.xml").isEmpty());
        assertTrue(imageStorageService.load("..\\pom.xml").isEmpty());
        assertTrue(imageStorageService.load("sub/dir.jpg").isEmpty());
    }

    @Test
    void rejectsUploadThatIsNotAnImage() {
        MockMultipartFile text = new MockMultipartFile(
                "file", "notes.jpg", "image/jpeg", "this is not a jpeg".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> imageStorageService.store(text));
        assertTrue(ex.getMessage().contains("valid image"), ex.getMessage());
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", fakeJpeg(64));
        assertThrows(BusinessException.class, () -> imageStorageService.store(pdf));
    }

    private byte[] fakeJpeg(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        for (int i = 3; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }
}
