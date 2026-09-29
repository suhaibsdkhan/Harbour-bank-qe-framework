package dev.suhaib.bank.transfer;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.suhaib.bank.account.Account;
import dev.suhaib.bank.account.AccountType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice: HTTP status mapping, validation and error bodies, with the service mocked. */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String BODY = """
            {"fromAccount":"1111111111","toAccount":"2222222222","amount":25.00}""";

    @Autowired MockMvc mvc;
    @MockitoBean TransferService service;

    private Transfer transfer() {
        return new Transfer(null, new Account("1111111111", "A", AccountType.CHEQUING),
                new Account("2222222222", "B", AccountType.SAVINGS), new BigDecimal("25.00"), null);
    }

    @Test
    void new_transfer_is_201_with_location() throws Exception {
        Transfer t = transfer();
        when(service.transfer(any(), eq(null))).thenReturn(new TransferService.Result(t, false));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transfers/" + t.getReference()))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")));
    }

    @Test
    void replay_is_200() throws Exception {
        when(service.transfer(any(), eq("k"))).thenReturn(new TransferService.Result(transfer(), true));

        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void validation_errors_are_400_problem_documents() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"\",\"toAccount\":\"2\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors.length()", equalTo(2)));
    }

    @Test
    void business_rejection_is_422_with_reference() throws Exception {
        when(service.transfer(any(), any())).thenThrow(new TransferRejectedException(
                dev.suhaib.bank.common.ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds", "ref-1"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_FUNDS")))
                .andExpect(jsonPath("$.transferReference", equalTo("ref-1")));
    }
}
