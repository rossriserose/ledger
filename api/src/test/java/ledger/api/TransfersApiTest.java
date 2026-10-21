package ledger.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransfersApiTest extends ApiTestSupport {

    @Test
    void transferIsAppliedAndRetryReturnsSameBody() throws Exception {
        account("a", 1000);
        account("b", 0);
        String body = "{\"from\":\"a\",\"to\":\"b\",\"amount\":250}";

        var first = post("/transfers", "key-1", body);
        var retry = post("/transfers", "key-1", body);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.body()).contains("\"status\":\"APPLIED\"", "\"amount\":250");
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.body()).isEqualTo(first.body());
        assertThat(get("/accounts/a").body()).contains("\"balance\":750");
        assertThat(get("/accounts/b").body()).contains("\"balance\":250");
        assertThat(get("/accounts/a/entries").body()).contains("\"amount\":-250");
    }

    @Test
    void mapsTransferErrorsToStatusCodes() throws Exception {
        account("a", 100);
        account("b", 0);

        assertThat(post("/transfers", null, "{\"from\":\"a\",\"to\":\"b\",\"amount\":10}").statusCode()).isEqualTo(400);
        assertThat(post("/transfers", "k1", "{\"from\":\"a\",\"to\":\"b\",\"amount\":10.5}").statusCode()).isEqualTo(400);
        assertThat(post("/transfers", "k2", "{\"from\":\"a\",\"to\":\"missing\",\"amount\":10}").statusCode()).isEqualTo(404);
        assertThat(post("/transfers", "k3", "{\"from\":\"a\",\"to\":\"b\",\"amount\":101}").statusCode()).isEqualTo(422);
        assertThat(post("/transfers", "k4", "{\"from\":\"a\",\"to\":\"a\",\"amount\":10}").statusCode()).isEqualTo(422);
        assertThat(post("/transfers", "k5", "{\"from\":\"a\",\"to\":\"b\",\"amount\":0}").statusCode()).isEqualTo(422);
        assertThat(post("/transfers", "k6", "{\"from\":\"a\",\"to\":\"b\",\"amount\":10}").statusCode()).isEqualTo(201);
        assertThat(post("/transfers", "k6", "{\"from\":\"a\",\"to\":\"b\",\"amount\":20}").statusCode()).isEqualTo(409);
    }

    @Test
    void rejectionIsRepeatedForSameKey() throws Exception {
        account("a", 100);
        account("b", 0);
        String body = "{\"from\":\"a\",\"to\":\"b\",\"amount\":500}";

        var first = post("/transfers", "key-1", body);
        var retry = post("/transfers", "key-1", body);

        assertThat(first.statusCode()).isEqualTo(422);
        assertThat(first.body()).contains("INSUFFICIENT_FUNDS");
        assertThat(retry.statusCode()).isEqualTo(422);
        assertThat(retry.body()).isEqualTo(first.body());
    }

    @Test
    void stringAmountGivesShortMessage() throws Exception {
        var response = post("/transfers", "k1", "{\"from\":\"a\",\"to\":\"b\",\"amount\":\"10\"}");

        assertThat(response.body()).isEqualTo(
                "{\"error\":\"BAD_REQUEST\",\"message\":\"amount must be an integer in minor units\"}");
    }
}
