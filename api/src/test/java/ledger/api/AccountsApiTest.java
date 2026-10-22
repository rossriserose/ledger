package ledger.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsApiTest extends ApiTestSupport {

    @Test
    void createsAndReadsAccount() throws Exception {
        var created = post("/accounts", null, "{\"id\":\"acc-1\",\"initialBalance\":10000,\"currency\":\"EUR\"}");
        var read = get("/accounts/acc-1");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).isEqualTo("{\"id\":\"acc-1\",\"balance\":10000,\"currency\":\"EUR\"}");
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(created.body());
    }

    @Test
    void newAccountHasNoEntries() throws Exception {
        account("acc-1", 100);

        assertThat(get("/accounts/acc-1/entries").body()).isEqualTo("[]");
        assertThat(get("/accounts/missing/entries").statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsBadAccountRequests() throws Exception {
        account("acc-1", 100);

        assertThat(post("/accounts", null, "{\"id\":\"acc-1\",\"initialBalance\":1,\"currency\":\"EUR\"}").statusCode()).isEqualTo(409);
        assertThat(post("/accounts", null, "{not json").statusCode()).isEqualTo(400);
        assertThat(post("/accounts", null, "{\"id\":\"x\",\"currency\":\"EUR\"}").statusCode()).isEqualTo(400);
        assertThat(post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":10.5,\"currency\":\"EUR\"}").statusCode()).isEqualTo(400);
        assertThat(post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":\"10\",\"currency\":\"EUR\"}").statusCode()).isEqualTo(400);
        assertThat(post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":-1,\"currency\":\"EUR\"}").statusCode()).isEqualTo(400);
        assertThat(post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":1,\"currency\":\"NOPE\"}").statusCode()).isEqualTo(400);
        assertThat(get("/accounts/missing").statusCode()).isEqualTo(404);
    }

    @Test
    void badJsonGivesShortMessageWithoutJacksonDetails() throws Exception {
        var floatAmount = post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":10.5,\"currency\":\"EUR\"}");
        var unknownField = post("/accounts", null, "{\"id\":\"x\",\"balance\":1,\"currency\":\"EUR\"}");
        var broken = post("/accounts", null, "{not json");
        var tooLarge = post("/accounts", null, "{\"id\":\"x\",\"initialBalance\":99999999999999999999,\"currency\":\"EUR\"}");

        assertThat(tooLarge.statusCode()).isEqualTo(400);
        assertThat(tooLarge.body()).isEqualTo(
                "{\"error\":\"BAD_REQUEST\",\"message\":\"initialBalance is out of range\"}");
        assertThat(floatAmount.body()).isEqualTo(
                "{\"error\":\"BAD_REQUEST\",\"message\":\"initialBalance must be an integer in minor units\"}");
        assertThat(unknownField.body()).isEqualTo(
                "{\"error\":\"BAD_REQUEST\",\"message\":\"unknown field balance\"}");
        assertThat(broken.body()).isEqualTo(
                "{\"error\":\"BAD_REQUEST\",\"message\":\"malformed json\"}");
    }
}
