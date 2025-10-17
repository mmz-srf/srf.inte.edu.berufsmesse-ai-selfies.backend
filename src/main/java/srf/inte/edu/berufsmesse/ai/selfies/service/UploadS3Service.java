package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.S3Properties;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadS3Service {

    private final S3Properties s3Properties;

    public String upload(String base64Image, String name) {
        byte[] image = Base64.getDecoder().decode(base64Image); // Turn base64 back into bytes
        log.info("Uploading {} (Bytes: {}) to S3 Bucket: {}, Region: {}",
                 name,
                 image.length,
                 s3Properties.getBucket(),
                 Region.EU_CENTRAL_1
        );

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                s3Properties.getAccessKey(),
                s3Properties.getSecretKey()
        );

        S3Client s3 = S3Client.builder()
                              .region(Region.EU_CENTRAL_1)
                              .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                              .build();

        PutObjectRequest request = PutObjectRequest.builder()
                                                   .bucket(s3Properties.getBucket())
                                                   .key(s3Properties.getUserPath() + name)
                                                   .contentType("image/png")
                                                   .build();

        s3.putObject(request, RequestBody.fromBytes(image));
        s3.close();

        return "https://www.srf.ch/staticfiles/berufsmesse/ki-pics/" + name;
    }
}
