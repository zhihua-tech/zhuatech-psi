/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.regex.Pattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@SpringBootTest @AutoConfigureMockMvc
class PsiCoreApiTests {
    @Autowired MockMvc mvc;
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void inventoryLedgerSupportsReceiptReservationReleaseAndIssue() throws Exception {
        create("PSI-CORE-1","WH-A",10); move("m1","PURCHASE_RECEIPT","PSI-CORE-1","WH-A",20).andExpect(jsonPath("$.data.afterOnHand").value(30));
        move("m2","RESERVE","PSI-CORE-1","WH-A",8).andExpect(jsonPath("$.data.afterReserved").value(8));
        move("m3","RELEASE","PSI-CORE-1","WH-A",3).andExpect(jsonPath("$.data.afterReserved").value(5));
        move("m4","SALES_ISSUE","PSI-CORE-1","WH-A",15).andExpect(jsonPath("$.data.afterOnHand").value(15));
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void inventoryPreventsNegativeAvailableStock() throws Exception {
        create("PSI-CORE-2","WH-A",5); move("m5","RESERVE","PSI-CORE-2","WH-A",5);
        move("m6","SALES_ISSUE","PSI-CORE-2","WH-A",1).andExpect(status().isConflict());
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void movementIsIdempotent() throws Exception {
        create("PSI-CORE-3","WH-A",0); move("same-key","PURCHASE_RECEIPT","PSI-CORE-3","WH-A",7);move("same-key","PURCHASE_RECEIPT","PSI-CORE-3","WH-A",7);
        mvc.perform(get("/api/core/psi/balances").param("warehouse","WH-A").with(httpBasic("operator","operator123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.sku=='PSI-CORE-3')].onHand").value(7));
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void transferPostsAtomicOutboundAndInbound() throws Exception {
        create("PSI-CORE-4","WH-A",20);create("PSI-CORE-4","WH-B",0);
        mvc.perform(post("/api/core/psi/transfers").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"t1\",\"referenceNo\":\"TR-1\",\"sku\":\"PSI-CORE-4\",\"fromWarehouse\":\"WH-A\",\"toWarehouse\":\"WH-B\",\"quantity\":8}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.outbound.afterOnHand").value(12)).andExpect(jsonPath("$.data.inbound.afterOnHand").value(8));
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void coreApisRequireAuthentication() throws Exception {mvc.perform(get("/api/core/psi/balances")).andExpect(status().isUnauthorized());}
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void approvedCycleCountPostsInventoryVariance() throws Exception {
        create("PSI-COUNT-1","WH-C",10);
        long id=createCount("COUNT-CORE-1","PSI-COUNT-1","WH-C",13);
        mvc.perform(post("/api/core/psi/counts/"+id+"/submit").with(httpBasic("operator","operator123"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/core/psi/counts/"+id+"/review").with(httpBasic("admin","admin123")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVE\",\"remark\":\"复核差异无误\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.variance").value(3)).andExpect(jsonPath("$.data.movementId").isNumber());
        mvc.perform(get("/api/core/psi/balances").param("warehouse","WH-C").with(httpBasic("operator","operator123")))
            .andExpect(jsonPath("$.data[?(@.sku=='PSI-COUNT-1')].onHand").value(13));
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void cycleCountReviewRequiresSeparationOfDuties() throws Exception {
        create("PSI-COUNT-2","WH-C",10);long id=createCount("COUNT-CORE-2","PSI-COUNT-2","WH-C",10);
        mvc.perform(post("/api/core/psi/counts/"+id+"/submit").with(httpBasic("admin","admin123"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/core/psi/counts/"+id+"/review").with(httpBasic("admin","admin123")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVE\",\"remark\":\"自行复核\"}")).andExpect(status().isConflict());
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @Test void inventoryChangeAfterCountForcesRecount() throws Exception {
        create("PSI-COUNT-3","WH-C",10);long id=createCount("COUNT-CORE-3","PSI-COUNT-3","WH-C",9);
        mvc.perform(post("/api/core/psi/counts/"+id+"/submit").with(httpBasic("operator","operator123"))).andExpect(status().isOk());
        move("count-race","PURCHASE_RECEIPT","PSI-COUNT-3","WH-C",1).andExpect(status().isOk());
        mvc.perform(post("/api/admin/core/psi/counts/"+id+"/review").with(httpBasic("admin","admin123")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVE\",\"remark\":\"复核\"}")).andExpect(status().isConflict());
    }
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    private void create(String sku,String warehouse,int opening)throws Exception{mvc.perform(post("/api/core/psi/balances").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\""+sku+"\",\"warehouse\":\""+warehouse+"\",\"openingQuantity\":"+opening+"}")).andExpect(status().isOk());}
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    private org.springframework.test.web.servlet.ResultActions move(String key,String type,String sku,String warehouse,int quantity)throws Exception{return mvc.perform(post("/api/core/psi/movements").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\""+key+"\",\"referenceNo\":\"REF-"+key+"\",\"type\":\""+type+"\",\"sku\":\""+sku+"\",\"warehouse\":\""+warehouse+"\",\"quantity\":"+quantity+"}"));}
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    private long createCount(String no,String sku,String warehouse,int counted)throws Exception{MvcResult r=mvc.perform(post("/api/core/psi/counts").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"countNo\":\""+no+"\",\"sku\":\""+sku+"\",\"warehouse\":\""+warehouse+"\",\"countedQuantity\":"+counted+"}")).andExpect(status().isOk()).andReturn();var m=Pattern.compile("\\\"id\\\":(\\d+)").matcher(r.getResponse().getContentAsString());m.find();return Long.parseLong(m.group(1));}
}
