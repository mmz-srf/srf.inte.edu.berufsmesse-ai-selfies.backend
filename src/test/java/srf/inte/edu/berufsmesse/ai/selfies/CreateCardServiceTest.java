package srf.inte.edu.berufsmesse.ai.selfies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.CardValueProperties;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardAura;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardJobsModel;
import srf.inte.edu.berufsmesse.ai.selfies.models.CreateCardServiceTestModel;
import srf.inte.edu.berufsmesse.ai.selfies.service.CreateCardService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateCardServiceTest {

    @Mock
    private CardJobsModel cardJobsModel;

    @Mock
    private DocumentProperties documentProperties;

    @Mock
    private CardValueProperties cardValueProperties;

    private CreateCardServiceTestModel testModel;
    private CreateCardService service;

    @BeforeEach
    void setup() {
        when(documentProperties.getAllowedTemplates()).thenReturn(List.of("aqua"));
        testModel = new CreateCardServiceTestModel();

        CardAura aura = mock(CardAura.class);
        when(aura.getMin()).thenReturn(3000);
        when(aura.getMax()).thenReturn(4000);
        when(aura.getStep()).thenReturn(100);
        Mockito.lenient().when(cardValueProperties.getAura()).thenReturn(aura);

        when(cardValueProperties.getJobs()).thenReturn(testModel.getJobs());

        when(documentProperties.getMaxNameLength()).thenReturn(100);

        service = new CreateCardService(documentProperties, cardValueProperties) {
            @Override
            public String getComfyUiImage(String imagePath, String comfyBaseUrl) {
                // Load AI dummy image as base64
                try (var inputStream = CreateCardService.class.getResourceAsStream("/ai.png")) {
                    assert inputStream != null;
                    byte[] imageBytes = inputStream.readAllBytes();
                    return Base64.getEncoder().encodeToString(imageBytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public int getRandomAura() {
                return 0;
            }

            @Override
            public CardJobsModel getRandomJob() {
                List<CardJobsModel> jobs = cardValueProperties.getJobs();
                return jobs.get((int) (Math.random() * jobs.size()));
            }

            @Override
            public String getRandomSuperpower() {
                return "Superpower";
            }

            @Override
            public String getRandomIck() {
                return "Ick";
            }
        };

        when(cardJobsModel.getName()).thenReturn("Test Job");
        when(cardJobsModel.getFontSize()).thenReturn(12);
    }

    @ParameterizedTest(name = "[{index}] Fixed Aura: {0}")
    @ValueSource(ints = { 3000, 3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000 })
    void createCards_withFixedAura_writesHtmlForInspection(int fixedAura) throws IOException {
        CreateCardRequest request = new CreateCardRequest();
        request.setAge(13);
        request.setName("Test Name");
        request.setTemplate("aqua");
        request.setImagePath("/ai.png");

        CreateCardService spy = spy(service);
        doReturn(fixedAura).when(spy).getRandomAura();

        String htmlResponse = spy.createCard(request);
        assertNotNull(htmlResponse);

        Files.createDirectories(Paths.get("target/html-reports"));
        Files.write(
                Paths.get("target/html-reports", fixedAura + ".html"),
                htmlResponse.getBytes(StandardCharsets.UTF_8)
        );

    }

    @Test
    void createCard_testJobLength() throws Exception {
        for (CardJobsModel job : testModel.getJobs()) {
            CreateCardRequest request = new CreateCardRequest();
            request.setAge(13);
            request.setName("Test Name");
            request.setTemplate("aqua");
            request.setImagePath("/ai.png");

            CreateCardService spy = spy(service);
            doReturn(job).when(spy).getRandomJob();

            String htmlResponse = spy.createCard(request);
            assertNotNull(htmlResponse);

            Files.createDirectories(Paths.get("target/html-reports"));
            String safeName = job.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
            safeName = safeName.replace("/", "(");
            Files.write(
                    Paths.get("target/html-reports", safeName + ".html"),
                    htmlResponse.getBytes(StandardCharsets.UTF_8)
            );

        }
    }
}
