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
import srf.inte.edu.berufsmesse.ai.selfies.service.StatisticsService;

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

    @Mock
    private StatisticsService statisticsService;

    private CreateCardServiceTestModel testModel;
    private  CreateCardRequest request;
    private CreateCardService spy;

    @BeforeEach
    void setup() {
        when(documentProperties.getAllowedTemplates()).thenReturn(List.of("aqua"));

        // Default request
        request = new CreateCardRequest();
        request.setAge(13);
        request.setName("Test Name");
        request.setTemplate("aqua");
        request.setImagePath("/ai.png");

        CardAura aura = mock(CardAura.class);
        when(aura.getMin()).thenReturn(3000);
        when(aura.getMax()).thenReturn(4000);
        when(aura.getStep()).thenReturn(100);
        Mockito.lenient().when(cardValueProperties.getAura()).thenReturn(aura);

        // Return actual list of jobs, icks and superpowers
        testModel = new CreateCardServiceTestModel();
        when(cardValueProperties.getJobs()).thenReturn(testModel.getJobs());
        when(cardValueProperties.getIck()).thenReturn(testModel.getIckList());
        when(cardValueProperties.getSuperpowers()).thenReturn(testModel.getSuperPowers());

        when(documentProperties.getMaxNameLength()).thenReturn(100);

        CreateCardService service = new CreateCardService(documentProperties, cardValueProperties, statisticsService) {
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
        };

        spy = spy(service);
    }

    @ParameterizedTest
    @ValueSource(ints = { 3000, 3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000 })
    void createCards_withFixedAura_writesHtmlForInspection(int fixedAura) throws IOException {
        doReturn(fixedAura).when(spy).getRandomAura();

        String htmlResponse = spy.createCard(request);
        assertNotNull(htmlResponse);

        Files.createDirectories(Paths.get("target/html-reports/aura"));
        Files.write(
                Paths.get("target/html-reports/aura", fixedAura + ".html"),
                htmlResponse.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void createCard_testJobLength() throws Exception {
        for (CardJobsModel job : testModel.getJobs()) {
            doReturn(job).when(spy).getRandomJob();

            String htmlResponse = spy.createCard(request);
            assertNotNull(htmlResponse);

            String safeName = job.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
            safeName = safeName.replace("/", "-");
            saveFile("target/html-reports/jobs", safeName, htmlResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void createCard_testIckLength() throws Exception {
        List<String> ick = cardValueProperties.getIck();
        for (int i = 0; i < ick.size(); i++) {
            String ickString = ick.get(i);
            doReturn(ickString).when(spy).getRandomIck();

            String htmlResponse = spy.createCard(request);
            assertNotNull(htmlResponse);

            saveFile("target/html-reports/icks", "ick(" + i + ")", htmlResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void createCard_testSuperpowerLength() throws Exception {
        List<String> superPowers = cardValueProperties.getSuperpowers();
        for (int i = 0; i < superPowers.size(); i++) {
            String superPower = superPowers.get(i);
            doReturn(superPower).when(spy).getRandomSuperpower();

            String htmlResponse = spy.createCard(request);
            assertNotNull(htmlResponse);

            saveFile("target/html-reports/superpowers", "superpower(" + i + ")", htmlResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void saveFile(String path, String fileName, byte[] data) throws IOException {
        Files.createDirectories(Paths.get(path));
        Files.write(
                Paths.get(path, fileName + "html"),
                data
        );
    }
}
