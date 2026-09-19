/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@SpringBootTest @AutoConfigureMockMvc class DomainDecisionApiTests {
 @Autowired MockMvc mvc;
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Test void domainDecisionReturnsAuditableScoreMetricsAndActions() throws Exception {
  mvc.perform(post("/api/domain/decision").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"documentNo\":\"PSI-SO-2026-001\",\"orderedQuantity\":100,\"availableQuantity\":120,\"unitPrice\":168,\"unitCost\":120,\"customerCreditAvailable\":30000,\"shelfLifeDaysRemaining\":180,\"priceApproved\":true,\"batchTraceable\":true}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").isString()).andExpect(jsonPath("$.data.score").isNumber()).andExpect(jsonPath("$.data.metrics").isMap()).andExpect(jsonPath("$.data.actions").isArray());
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Test void domainRiskScenarioReturnsExpectedBlockingDecision() throws Exception {
  mvc.perform(post("/api/domain/decision").with(httpBasic("operator","operator123")).contentType(MediaType.APPLICATION_JSON).content("{\"documentNo\":\"PSI-SO-2026-001\",\"orderedQuantity\":200,\"availableQuantity\":5,\"unitPrice\":50,\"unitCost\":100,\"customerCreditAvailable\":10,\"shelfLifeDaysRemaining\":3,\"priceApproved\":false,\"batchTraceable\":false}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("BLOCKED")).andExpect(jsonPath("$.data.actions").isNotEmpty());
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Test void domainDecisionRequiresAuthentication() throws Exception {mvc.perform(post("/api/domain/decision").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());}
}
