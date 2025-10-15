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
import software.amazon.awssdk.services.s3.model.WriteGetObjectResponseRequest;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.S3Properties;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadS3Service {

    private final S3Properties s3Properties;

    public String upload(byte[] image, String name) {
        log.info("Uploading {} to S3 Bucket: {}, Region: {}, URI: {}",
                 name,
                 s3Properties.getBucket(),
                 Region.EU_CENTRAL_1,
                 s3Properties.getUri()
        );

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                System.getenv("AWS_ACCESS_KEY_ID"),
                System.getenv("AWS_SECRET_ACCESS_KEY")
        );

        S3Client s3 = S3Client.builder()
                              .region(Region.EU_CENTRAL_1)
                              .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                              .endpointOverride(URI.create(s3Properties.getUri()))
                              .forcePathStyle(true)
                              .build();


        log.info("Connecting to bucket: {} for path: {}", s3Properties.getBucket(), s3Properties.getUserPath() + name);

        PutObjectRequest request = PutObjectRequest.builder()
                                                   .bucket(s3Properties.getBucket())
                                                   .key(s3Properties.getUserPath() + name)
                                                   .contentType("image/png")
                                                   .build();

        s3.putObject(request, RequestBody.fromBytes(image));
        s3.close();

        return "link to image on s3";
    }
}
