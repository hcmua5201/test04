import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.*;

public class SearchByPageTest {

    private int page;
    private int size;
    private static final String API_ENDPOINT = "/api/products";

    @BeforeTest
    public void setUp() {
        System.out.println("初始化测试环境");
        RestAssured.baseURI = "http://154.219.117.53:5000";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test(description = "模拟设置page和size参数", priority = 0)
    public void testSetPageAndSize() {
        System.out.println("设置page和size参数");
        page = 1;
        size = 2;
    }

    @Test(description = "分页查询接口正常测试", priority = 1, dependsOnMethods = {"testSetPageAndSize"})
    public void testSearchByPage() {
        System.out.println("分页查询接口正常测试");
        assertNotEquals(page, 0, "page参数未设置");
        assertNotEquals(size, 0, "size参数未设置");
        System.out.println("前置page和size参数设置校验成功");

        // 使用List<Map>来正确解析JSON对象
        List<Map<String, Object>> items = RestAssured
                .given()
                .contentType("application/json")
                .param("page", this.page)
                .param("size", this.size)
                .when()
                .get(API_ENDPOINT)
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList("items");

        System.out.println("请求接口成功，返回结果: " + items);

        // 修改校验逻辑
        for (Map<String, Object> item : items) {
            assertTrue(item.containsKey("id"), "分页查询结果缺少id字段");
            assertTrue(item.containsKey("name"), "分页查询结果缺少name字段");
            assertTrue(item.containsKey("price"), "分页查询结果缺少price字段");
        }

        System.out.println("分页查询结果数据校验成功");
        System.out.println("期望size: " + this.size);
        System.out.println("实际返回数量: " + items.size());
        assertEquals(items.size(), this.size, "分页查询结果数量与size参数不一致");
    }

    @Test(description = "单请求响应时间性能测试", priority = 2)

    public void testSingleRequestResponseTime() {
        System.out.println("=== 单请求响应时间性能测试 ===");

        long startTime = System.currentTimeMillis();

        Response response = RestAssured
                .given()
                .contentType("application/json")
                .param("page", 1)
                .param("size", 5)
                .when()
                .get(API_ENDPOINT);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        System.out.println("单请求响应时间: " + responseTime + "ms");

        // 验证响应数据完整性
        List<Map<String, Object>> items = response.jsonPath().getList("items");
        assertNotNull(items, "响应数据不应为null");
        assertFalse(items.isEmpty(), "响应数据不应为空");

        // 性能断言：单请求响应时间应小于6500ms
        assertTrue(responseTime < 6500, "单请求响应时间超过6500ms，实际: " + responseTime + "ms");
        assertEquals(response.getStatusCode(), 200, "HTTP状态码应该为200");

        System.out.println("✅ 单请求性能测试通过");
    }

    @Test(description = "连续请求性能测试", priority = 3)
    public void testContinuousRequestsPerformance() {
        System.out.println("=== 连续请求性能测试 ===");

        int requestCount = 10;
        long totalResponseTime = 0;
        long maxResponseTime = 0;
        long minResponseTime = Long.MAX_VALUE;
        int validDataCount = 0; // 统计有有效数据的请求

        for (int i = 1; i <= requestCount; i++) {
            long startTime = System.currentTimeMillis();

            Response response = RestAssured
                    .given()
                    .contentType("application/json")
                    .param("page", i)
                    .param("size", 1)
                    .when()
                    .get(API_ENDPOINT);

            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;

            // 验证业务数据
            List<Map<String, Object>> items = response.jsonPath().getList("items");
            if (items != null && !items.isEmpty()) {
                validDataCount++;
            }

            totalResponseTime += responseTime;
            maxResponseTime = Math.max(maxResponseTime, responseTime);
            minResponseTime = Math.min(minResponseTime, responseTime);

            System.out.println("第 " + i + " 次请求响应时间: " + responseTime + "ms, 数据量: " +
                    (items != null ? items.size() : 0));

            // 每次请求间短暂停顿
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long averageResponseTime = totalResponseTime / requestCount;
        double validDataRate = (double) validDataCount / requestCount * 100;

        System.out.println("=== 性能测试结果 ===");
        System.out.println("总请求数: " + requestCount);
        System.out.println("有效数据请求: " + validDataCount);
        System.out.println("数据有效率: " + String.format("%.2f", validDataRate) + "%");
        System.out.println("平均响应时间: " + averageResponseTime + "ms");
        System.out.println("最大响应时间: " + maxResponseTime + "ms");
        System.out.println("最小响应时间: " + minResponseTime + "ms");

        // 性能断言
        assertTrue(averageResponseTime < 300, "平均响应时间超过300ms，实际: " + averageResponseTime + "ms");
        assertTrue(maxResponseTime < 1000, "最大响应时间超过1000ms，实际: " + maxResponseTime + "ms");
        assertTrue(validDataRate >= 50, "有效数据率低于50%，实际: " + validDataRate + "%");

        System.out.println("✅ 连续请求性能测试通过");
    }

    @Test(description = "并发压力测试 - 多线程同时请求", priority = 4,
            threadPoolSize = 5, invocationCount = 10, timeOut = 10000)
    public void testConcurrentLoad() {
        System.out.println("线程 " + Thread.currentThread().getId() + " 开始执行压力测试");

        long startTime = System.currentTimeMillis();

        Response response = RestAssured
                .given()
                .contentType("application/json")
                .param("page", 1)
                .param("size", 2)
                .when()
                .get(API_ENDPOINT);

        long responseTime = System.currentTimeMillis() - startTime;

        // 验证在高并发下接口仍然正常工作
        assertEquals(response.getStatusCode(), 200,
                "并发请求下HTTP状态码应该为200，线程: " + Thread.currentThread().getId());

        // 验证响应数据格式
        List<Map<String, Object>> items = response.jsonPath().getList("items");
        assertNotNull(items, "响应中应该包含items字段");

        // 如果有数据，验证数据格式
        if (!items.isEmpty()) {
            for (Map<String, Object> item : items) {
                assertTrue(item.containsKey("id"), "产品应包含id字段");
                assertTrue(item.containsKey("name"), "产品应包含name字段");
                assertTrue(item.containsKey("price"), "产品应包含price字段");
            }
        }

        System.out.println("线程 " + Thread.currentThread().getId() +
                " 压力测试完成，响应时间: " + responseTime + "ms, 数据量: " + items.size());

        // 并发下的性能要求可以适当放宽
        assertTrue(responseTime < 2000,
                "并发请求响应时间超过2000ms，实际: " + responseTime + "ms");
    }

    @Test(description = "高负载压力测试", priority = 5)
    public void testHighLoadStress() {
        System.out.println("=== 高负载压力测试 ===");

        int concurrentUsers = 20;
        int requestsPerUser = 5;
        int totalRequests = concurrentUsers * requestsPerUser;

        long testStartTime = System.currentTimeMillis();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger emptyDataCount = new AtomicInteger(0);
        AtomicInteger validDataCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        Thread[] threads = new Thread[concurrentUsers];

        for (int i = 0; i < concurrentUsers; i++) {
            final int userIndex = i + 1; // 页码从1开始
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < requestsPerUser; j++) {
                        try {
                            long startTime = System.currentTimeMillis();

                            Response response = RestAssured
                                    .given()
                                    .contentType("application/json")
                                    .param("page", userIndex)
                                    .param("size", 1)
                                    .when()
                                    .get(API_ENDPOINT);

                            long responseTime = System.currentTimeMillis() - startTime;
                            totalResponseTime.addAndGet(responseTime);

                            int statusCode = response.getStatusCode();

                            if (statusCode == 200) {
                                // 解析响应数据，验证业务层面是否成功
                                List<Map<String, Object>> items = response.jsonPath().getList("items");

                                if (items != null && !items.isEmpty()) {
                                    successCount.incrementAndGet();
                                    validDataCount.incrementAndGet();
                                    // 验证数据格式
                                    for (Map<String, Object> item : items) {
                                        assert item.containsKey("id") : "缺少id字段";
                                        assert item.containsKey("name") : "缺少name字段";
                                        assert item.containsKey("price") : "缺少price字段";
                                    }
                                } else {
                                    emptyDataCount.incrementAndGet();
                                    successCount.incrementAndGet(); // HTTP层面还是成功的
                                }
                            } else {
                                failureCount.incrementAndGet();
                            }

                            // 随机延迟，模拟真实用户行为
                            Thread.sleep((long) (Math.random() * 50));

                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long testDuration = System.currentTimeMillis() - testStartTime;
        long averageResponseTime = totalResponseTime.get() / totalRequests;
        double httpSuccessRate = (double) successCount.get() / totalRequests * 100;
        double validDataRate = (double) validDataCount.get() / totalRequests * 100;
        double emptyDataRate = (double) emptyDataCount.get() / totalRequests * 100;
        double requestsPerSecond = (double) totalRequests / (testDuration / 1000.0);

        System.out.println("=== 详细的压力测试结果 ===");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("HTTP成功请求: " + successCount.get());
        System.out.println("有效数据请求: " + validDataCount.get() + " (有业务数据)");
        System.out.println("空数据请求: " + emptyDataCount.get() + " (业务数据为空)");
        System.out.println("失败请求: " + failureCount.get());
        System.out.println("HTTP成功率: " + String.format("%.2f", httpSuccessRate) + "%");
        System.out.println("有效数据率: " + String.format("%.2f", validDataRate) + "%");
        System.out.println("空数据率: " + String.format("%.2f", emptyDataRate) + "%");
        System.out.println("测试持续时间: " + testDuration + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", requestsPerSecond) + " 请求/秒");
        System.out.println("平均响应时间: " + averageResponseTime + "ms");

        // 压力测试断言
        assertTrue(httpSuccessRate >= 95, "HTTP成功率低于95%，实际: " + httpSuccessRate + "%");
        assertTrue(validDataRate >= 30, "有效数据率低于30%，实际: " + validDataRate + "%");
        assertTrue(averageResponseTime < 1000, "平均响应时间超过1000ms，实际: " + averageResponseTime + "ms");
        assertTrue(requestsPerSecond > 10, "吞吐量低于10请求/秒，实际: " + requestsPerSecond);

        System.out.println("✅ 高负载压力测试通过");
    }

    @Test(description = "边界值测试", priority = 6)
    public void testBoundaryValues() {
        System.out.println("=== 边界值测试 ===");

        // 测试第一页
        Response response1 = RestAssured
                .given()
                .contentType("application/json")
                .param("page", 1)
                .param("size", 2)
                .when()
                .get(API_ENDPOINT);

        assertEquals(response1.getStatusCode(), 200, "第一页应该返回200");
        List<Map<String, Object>> items1 = response1.jsonPath().getList("items");
        assertNotNull(items1, "第一页数据不应为null");
        System.out.println("第一页数据量: " + items1.size());

        // 测试较大的页码（根据你的数据，大概有10个商品）
        Response response2 = RestAssured
                .given()
                .contentType("application/json")
                .param("page", 10)
                .param("size", 2)
                .when()
                .get(API_ENDPOINT);

        assertEquals(response2.getStatusCode(), 200, "较大页码应该返回200");
        List<Map<String, Object>> items2 = response2.jsonPath().getList("items");
        assertNotNull(items2, "较大页码数据不应为null");
        System.out.println("第10页数据量: " + items2.size());

        // 测试不同的size
        Response response3 = RestAssured
                .given()
                .contentType("application/json")
                .param("page", 1)
                .param("size", 5)
                .when()
                .get(API_ENDPOINT);

        assertEquals(response3.getStatusCode(), 200, "size=5应该返回200");
        List<Map<String, Object>> items3 = response3.jsonPath().getList("items");
        assertNotNull(items3, "size=5数据不应为null");
        assertTrue(items3.size() <= 5, "返回数据量不应超过请求的size");
        System.out.println("size=5时数据量: " + items3.size());

        System.out.println("✅ 边界值测试通过");
    }

    @Test(description = "数据完整性验证", priority = 7)
    public void testDataIntegrity() {
        System.out.println("=== 数据完整性验证 ===");

        // 测试前5页的数据完整性
        for (int page = 1; page <= 5; page++) {
            Response response = RestAssured
                    .given()
                    .contentType("application/json")
                    .param("page", page)
                    .param("size", 2)
                    .when()
                    .get(API_ENDPOINT);

            assertEquals(response.getStatusCode(), 200, "页码 " + page + " 应该返回200");

            List<Map<String, Object>> items = response.jsonPath().getList("items");
            assertNotNull(items, "页码 " + page + " 数据不应为null");

            if (!items.isEmpty()) {
                System.out.println("页码 " + page + " 有数据，数量: " + items.size());

                // 验证每个产品的数据完整性
                for (Map<String, Object> item : items) {
                    assertTrue(item.containsKey("id"), "产品应包含id字段");
                    assertTrue(item.containsKey("name"), "产品应包含name字段");
                    assertTrue(item.containsKey("price"), "产品应包含price字段");

                    // 验证数据类型
                    assertTrue(item.get("id") instanceof Integer, "id应该是整数类型");
                    assertTrue(item.get("name") instanceof String, "name应该是字符串类型");
                    assertTrue(item.get("price") instanceof Number, "price应该是数字类型");

                    System.out.println("  产品: id=" + item.get("id") +
                            ", name=" + item.get("name") +
                            ", price=" + item.get("price"));
                }
            } else {
                System.out.println("页码 " + page + " 无数据");
            }
        }

        System.out.println("✅ 数据完整性验证通过");
    }
}