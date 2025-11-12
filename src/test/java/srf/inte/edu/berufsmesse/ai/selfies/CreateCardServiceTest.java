package srf.inte.edu.berufsmesse.ai.selfies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.CardValueProperties;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardAura;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardJobsModel;
import srf.inte.edu.berufsmesse.ai.selfies.service.CreateCardService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateCardServiceTest {

    @Mock
    private static CardJobsModel cardJobsModel;

    @Mock
    private DocumentProperties documentProperties;

    @Mock
    private CardValueProperties cardValueProperties;

    private CreateCardService service;

    @BeforeEach
    void setup() {
        when(documentProperties.getAllowedTemplates()).thenReturn(List.of("aqua"));

        CardAura aura = mock(CardAura.class);
        when(cardValueProperties.getAura()).thenReturn(aura);
        when(cardValueProperties.getJobs()).thenReturn(List.of(cardJobsModel));
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

    @ParameterizedTest
    @ValueSource(ints = { 3000, 3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000 })
    void createCards_withFixedAura_writesHtmlForInspection(int fixedAura) throws IOException {
        // create request
        CreateCardRequest request = new CreateCardRequest();
        request.setAge(13);
        request.setName("Test Name");
        request.setTemplate("aqua");
        request.setImagePath("/ai.png");


        String htmlResponse = service.createCard(request);
        assertNotNull(htmlResponse);
    }

    static Stream<CardJobsModel> jobsProvider() { return Stream.of(cardJobsModel); }

    @ParameterizedTest
    @MethodSource("jobsProvider")
    void createCard_testJobLength(CardJobsModel job) throws Exception {
        CreateCardRequest request = new CreateCardRequest();
        request.setAge(13);
        request.setName("Test Name");
        request.setTemplate("aqua");
        request.setImagePath("/ai.png");

        CreateCardService spy = spy(service);
        doReturn(job).when(spy).getRandomJob();

        String htmlResponse = spy.createCard(request);
        assertNotNull(htmlResponse);
    }
}
