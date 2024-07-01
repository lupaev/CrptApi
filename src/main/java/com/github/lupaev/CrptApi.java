package com.github.lupaev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.concurrent.*;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String SIGNATURE_HEADER = "Signature";

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();

        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();

        HttpPost httpPost = new HttpPost(API_URL);
        httpPost.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        httpPost.setHeader(SIGNATURE_HEADER, signature);

        String json = objectMapper.writeValueAsString(document);
        httpPost.setEntity(new StringEntity(json));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            if (response.getCode() == 200) {
                System.out.println(response.getCode() + " " + response.getReasonPhrase());
            } else {
                System.out.println(response.getCode());
            }
        } finally {
            semaphore.release();
        }
    }

    public record Document(
            Description description,
            String doc_id,
            String doc_status,
            String doc_type,
            boolean importRequest,
            String owner_inn,
            String participant_inn,
            String producer_inn,
            String production_date,
            String production_type,
            Product[] products,
            String reg_date,
            String reg_number
    ) {
        public record Description(String participantInn) {}
        public record Product(
                String certificate_document,
                String certificate_document_date,
                String certificate_document_number,
                String owner_inn,
                String producer_inn,
                String production_date,
                String tnved_code,
                String uit_code,
                String uitu_code
        ) {}
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        Document.Description description = new Document.Description("1234567890");
        Document.Product product = new Document.Product(
                "cert",
                "2020-01-23",
                "cert123",
                "1234567890",
                "1234567890",
                "2020-01-23",
                "code",
                "uit",
                "uitu"
        );
        Document document = new Document(
                description,
                "doc123",
                "NEW",
                "LP_INTRODUCE_GOODS",
                true,
                "1234567890",
                "1234567890",
                "1234567890",
                "2020-01-23",
                "type",
                new Document.Product[]{product},
                "2020-01-23",
                "reg123"
        );

        api.createDocument(document, "signature");
    }
}
