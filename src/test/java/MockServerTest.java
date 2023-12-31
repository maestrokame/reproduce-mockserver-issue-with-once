import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class MockServerTest {

    private static final int THREAD_SIZE = 50;

    @Test
    void onlyOneRequestShouldGetTheMockedResponse() throws Exception {

        MockServerClient mockServer = ClientAndServer.startClientAndServer(3000);
        mockServer.when(request("/output/.*").withMethod("GET"), Times.once())
                .respond(response()
                        .withStatusCode(200)
                        .withBody("OK"));

        AtomicInteger mockedResponseCount = new AtomicInteger();
        CountDownLatch warmUp = new CountDownLatch(THREAD_SIZE);
        Runnable runnable = () -> {
            try {
                warmUp.countDown();
                warmUp.await();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:3000/output/" + UUID.randomUUID()))
                        .GET()
                        .build();

                HttpResponse<String> response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                if ("OK".equals(response.body())) {
                    mockedResponseCount.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_SIZE);
        IntStream.range(0, THREAD_SIZE).forEach((i) -> executor.submit(runnable));

        executor.awaitTermination(1, TimeUnit.SECONDS);
        mockServer.stop();

        assertEquals(1, mockedResponseCount.get());
    }
}
