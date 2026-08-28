/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PsiCoreApiTests {
    @Autowired MockMvc mvc;
    @Test void inventoryLedgerSupportsReceiptReservationReleaseAndIssue() throws Exception {
        create("PSI-CORE-1","WH-A",10); move("m1","PURCHASE_RECEIPT","PSI-CORE-1","WH-A",20).andExpect(jsonPath("$.data.afterOnHand").value(30));
        move("m2","RESERVE","PSI-CORE-1","WH-A",8).andExpect(jsonPath("$.data.afterReserved").value(8));
        move("m3","RELEASE","PSI-CORE-1","WH-A",3).andExpect(jsonPath("$.data.afterReserved").value(5));
        move("m4","SALES_ISSUE","PSI-CORE-1","WH-A",15).andExpect(jsonPath("$.data.afterOnHand").value(15));
    }
    @Test void inventoryPreventsNegativeAvailableStock() throws Exception {
        create("PSI-CORE-2","WH-A",5); move("m5","RESERVE","PSI-CORE-2","WH-A",5);
        move("m6","SALES_ISSUE","PSI-CORE-2","WH-A",1).andExpect(status().isConflict());
    }
    @Test void movementIsIdempotent() throws Exception {
        create("PSI-CORE-3","WH-A",0); move("same-key","PURCHASE_RECEIPT","PSI-CORE-3","WH-A",7);move("same-key","PURCHASE_RECEIPT","PSI-CORE-3","WH-A",7);
        mvc.perform(get("/api/core/psi/balances").param("warehouse","WH-A").with(httpBasic("operator","operator123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.sku=='PSI-CORE-3')].onHand").value(7));
    }
    @Test void transferPostsAtomicOutboundAndInbound() throws Exception {
        create("PSI-CORE-4","WH-A",20);create("PSI-CORE-4","WH-B",0);
        mvc.perform(post("/api/core/psi/transfers").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"t1\",\"referenceNo\":\"TR-1\",\"sku\":\"PSI-CORE-4\",\"fromWarehouse\":\"WH-A\",\"toWarehouse\":\"WH-B\",\"quantity\":8}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.outbound.afterOnHand").value(12)).andExpect(jsonPath("$.data.inbound.afterOnHand").value(8));
    }
    @Test void coreApisRequireAuthentication() throws Exception {mvc.perform(get("/api/core/psi/balances")).andExpect(status().isUnauthorized());}
    private void create(String sku,String warehouse,int opening)throws Exception{mvc.perform(post("/api/core/psi/balances").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\""+sku+"\",\"warehouse\":\""+warehouse+"\",\"openingQuantity\":"+opening+"}")).andExpect(status().isOk());}
    private org.springframework.test.web.servlet.ResultActions move(String key,String type,String sku,String warehouse,int quantity)throws Exception{return mvc.perform(post("/api/core/psi/movements").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\""+key+"\",\"referenceNo\":\"REF-"+key+"\",\"type\":\""+type+"\",\"sku\":\""+sku+"\",\"warehouse\":\""+warehouse+"\",\"quantity\":"+quantity+"}"));}
}
