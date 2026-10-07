package com.bioplatform.service.impl;

import com.bioplatform.entity.DataFile;
import com.bioplatform.mapper.DataFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 分片合并 MD5 校验与秒传查询测试
 * - hash 正确：合并成功，入库 contentHash = 服务端实算值
 * - hash 错误：抛 IllegalStateException，目标文件已删除，不入库
 * - findByHash 空值防御：不查库直接返回 null
 */
class ChunkUploadHashTest {

    @TempDir
    Path tempDir;

    private DataFileMapper mapper;
    private ChunkUploadServiceImpl service;

    private byte[] chunk0;
    private byte[] chunk1;
    private String fullHash;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(DataFileMapper.class);
        service = new ChunkUploadServiceImpl(mapper);
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        chunk0 = "hello ".getBytes();
        chunk1 = "world".getBytes();
        MessageDigest d = MessageDigest.getInstance("MD5");
        d.update(chunk0);
        d.update(chunk1);
        fullHash = HexFormat.of().formatHex(d.digest());
    }

    private void prepareChunks(String uploadId) throws IOException {
        Path dir = tempDir.resolve("_chunks").resolve(uploadId);
        Files.createDirectories(dir);
        Files.write(dir.resolve("0"), chunk0);
        Files.write(dir.resolve("1"), chunk1);
        Files.writeString(dir.resolve("meta.json"),
                "{\"fileName\":\"test.txt\",\"totalChunks\":2}");
    }

    @Test
    void mergeWithCorrectHashStoresServerHash() throws Exception {
        prepareChunks("up1");

        DataFile result = service.mergeChunks("up1", "test.txt", 1L, 1L, fullHash);

        assertEquals(fullHash, result.getContentHash());
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(mapper).insert(captor.capture());
        assertEquals(fullHash, captor.getValue().getContentHash());
        // 合并产物存在且内容完整
        Path projectDir = tempDir.resolve("1");
        try (Stream<Path> files = Files.list(projectDir)) {
            Path merged = files.findAny().orElseThrow();
            assertEquals("hello world", Files.readString(merged));
        }
        // 分片目录已清理
        assertFalse(Files.exists(tempDir.resolve("_chunks").resolve("up1")));
    }

    @Test
    void mergeWithWrongHashRejectsAndDeletesTarget() throws IOException {
        prepareChunks("up2");

        assertThrows(IllegalStateException.class,
                () -> service.mergeChunks("up2", "test.txt", 1L, 1L,
                        "deadbeefdeadbeefdeadbeefdeadbeef"));

        verify(mapper, never()).insert(any());
        // 目标文件已删除：项目目录里没有残留
        Path projectDir = tempDir.resolve("1");
        try (Stream<Path> files = Files.list(projectDir)) {
            assertTrue(files.findAny().isEmpty(), "校验失败后不应残留目标文件");
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void mergeWithoutHashStillWorks() throws Exception {
        prepareChunks("up3");

        // 老客户端不带 fileHash：不校验，但服务端仍实算入库
        DataFile result = service.mergeChunks("up3", "test.txt", 1L, 1L, null);

        assertEquals(fullHash, result.getContentHash());
    }

    @Test
    void findByHashNullSafe() {
        assertNull(service.findByHash(null, 1L));
        assertNull(service.findByHash("  ", 1L));
        assertNull(service.findByHash(fullHash, null));
        verify(mapper, never()).selectByHashAndProject(any(), any());
    }

    @Test
    void findByHashDelegatesToMapper() {
        DataFile existing = new DataFile();
        existing.setId(42L);
        when(mapper.selectByHashAndProject(fullHash, 7L)).thenReturn(existing);

        assertSame(existing, service.findByHash(fullHash, 7L));
    }
}
