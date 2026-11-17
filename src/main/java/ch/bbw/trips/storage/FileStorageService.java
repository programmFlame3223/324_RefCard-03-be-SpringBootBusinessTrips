package ch.bbw.trips.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    public record StoredImage(Resource resource, String contentType, long contentLength) {}

    private final S3Client s3;
    private final String bucket;

    public FileStorageService(S3Client s3, @Value("${app.storage.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Datei ist leer");
        }
        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new IllegalArgumentException("Nicht unterstützter Dateityp: " + file.getContentType());
        }

        String key = UUID.randomUUID() + "." + extension;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return key;
    }

    public StoredImage load(String key) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        ResponseInputStream<GetObjectResponse> stream = s3.getObject(request);
        GetObjectResponse meta = stream.response();
        return new StoredImage(new InputStreamResource(stream), meta.contentType(), meta.contentLength());
    }
}