package com.apps.deen_sa.integration;

import com.apps.deen_sa.conversation.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
public abstract class AbstractIntegrationTestProperties {

	@Autowired
	private Flyway flyway;

	@Autowired
	private ConversationContext conversationContext;

	@Value("${wiremock.admin-url:http://localhost:9091/__admin}")
	private String wireMockAdminUrl;

	@BeforeEach
	protected void resetTestState(TestInfo testInfo) {
		flyway.clean();
		flyway.migrate();
		conversationContext.reset();
		resetAndLoadWireMockMappings(testInfo);
	}

	private void resetAndLoadWireMockMappings(TestInfo testInfo) {
		String testFolder = testInfo.getTestMethod()
				.orElseThrow(() -> new IllegalStateException("Unable to determine the current test method"))
				.getName();
		String mappingPattern = "classpath*:wiremock/%s/wiremock/mappings/*.json".formatted(testFolder);

		try {
			Resource[] mappings = new PathMatchingResourcePatternResolver().getResources(mappingPattern);
			if (mappings.length == 0) {
				mappings = new PathMatchingResourcePatternResolver()
						.getResources("classpath*:wiremock/it_001/wiremock/mappings/*.json");
			}

			RestClient wireMockAdmin = RestClient.create(wireMockAdminUrl);
			wireMockAdmin.delete().uri("/mappings").retrieve().toBodilessEntity();
			wireMockAdmin.delete().uri("/requests").retrieve().toBodilessEntity();

			for (Resource mapping : mappings) {
				wireMockAdmin.post()
						.uri("/mappings")
						.contentType(MediaType.APPLICATION_JSON)
						.body(mapping.getContentAsString(StandardCharsets.UTF_8))
						.retrieve()
						.toBodilessEntity();
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load WireMock mappings from " + mappingPattern, exception);
		}
	}

}
