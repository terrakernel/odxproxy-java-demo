package com.terrakernel.odx;

import io.odxproxy.OdxProxy;
import io.odxproxy.client.OdxProxyClientInfo;
import io.odxproxy.model.OdxClientKeywordRequest;
import io.odxproxy.model.OdxInstanceInfo;
import io.odxproxy.model.OdxServerResponse;
import io.odxproxy.model.OdxClientRequestContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;
import kotlinx.serialization.json.JsonArray;

// Service Layer: Handles ODXProxy communication and data mapping
public class OdxClient {

    public OdxClient() {
        // Initialization logic moved here. Reads environment variables.
        String odooUrl = System.getenv("ODOO_BASE_URL");
        String odooDB = System.getenv("ODOO_DB");
        String odooApiKey = System.getenv("ODOO_API_KEY");
        String odxApiKey = System.getenv("ODX_API_KEY");
        int userId = 2; // Assuming fixed user ID

        try {
            OdxInstanceInfo info = new OdxInstanceInfo(odooUrl, userId, odooDB, odooApiKey);
            OdxProxyClientInfo odxConfig = new OdxProxyClientInfo(info, odxApiKey);
            OdxProxy.init(odxConfig);
        } catch (Exception e) {
            // It's critical to catch init errors here
            throw new RuntimeException("Failed to initialize ODXProxyClient", e);
        }
    }

    /* PARTNER */
    // Public method that returns a Future with a clean List of Partner objects
    public CompletableFuture<List<Partner>> fetchPartners() {
        // --- Request Setup ---
        List<Integer> allowedCompanies = List.of(1);
        OdxClientRequestContext requestContext = new OdxClientRequestContext(
             allowedCompanies, 1, "Asia/Jakarta", "en_US"
        );
        
        List<String> fields = List.of("id", "name", "email", "street", "street2", "city", 
                                      "country_id", "phone", "customer_rank", "supplier_rank", "vat");
        Integer limit = 5;
        Integer offset = 0;
        
        OdxClientKeywordRequest keywords = new OdxClientKeywordRequest(
            fields, null, limit, offset, requestContext
        );

        // --- Execute and Map ---
        CompletableFuture<OdxServerResponse<List<JsonElement>>> futureRaw = OdxProxy.searchRead(
            "res.partner", 
            List.of(), 
            keywords, 
            null, 
            JsonElement.class 
        );

        // Use thenApply to chain the parsing logic
        return futureRaw.thenApply(this::parseAndMapResponse);
    }

    // Private method to handle error checking and mapping (former processResponse logic)
    private List<Partner> parseAndMapResponse(OdxServerResponse<List<JsonElement>> response) {
        if (response.getError() != null) {
            // Convert ODX error into a Java exception
            throw new RuntimeException("ODX Server Error: " + response.getError().getMessage());
        }

        List<JsonElement> rawResults = response.getResult();
        if (rawResults == null) {
            return List.of();
        }

        // Map raw JSON elements to clean Partner objects
        return rawResults.stream()
            .map(element -> (JsonObject) element)
            .map(this::createPartnerFromJsonObject)
            .collect(Collectors.toList());
    }

    // Private method to encapsulate the low-level JSON parsing
    private Partner createPartnerFromJsonObject(JsonObject customerJson) {
        Partner p = new Partner();
        
        java.util.function.Function<String, String> safeGet = key -> {
            JsonElement el = customerJson.get(key);
            if (el != null && el instanceof JsonPrimitive) {
                return ((JsonPrimitive) el).getContent();
            }
            return "";
        };

        // --- Parsing Logic ---
        p.id = Integer.parseInt(safeGet.apply("id"));
        p.name = safeGet.apply("name");
        p.email = safeGet.apply("email");
        p.street = safeGet.apply("street");
        p.street2 = safeGet.apply("street2");
        p.city = safeGet.apply("city");
        p.phone = safeGet.apply("phone");
        p.vat = safeGet.apply("vat");

        // Many2One field
        JsonElement countryElement = customerJson.get("country_id");
        if (countryElement != null && countryElement instanceof JsonArray) {
            p.country = ((JsonPrimitive) ((JsonArray) countryElement).get(1)).getContent();
        } else {
            p.country = "";
        }

        // Rank fields
        p.isCustomer = Integer.parseInt(safeGet.apply("customer_rank")) > 0;
        p.isSupplier = Integer.parseInt(safeGet.apply("supplier_rank")) > 0;

        return p;
    }

    /* PRODUCT */
    public CompletableFuture<List<Product>> fetchProducts() {
        // --- Request Setup ---
        List<Integer> allowedCompanies = List.of(1);
        OdxClientRequestContext requestContext = new OdxClientRequestContext(
             allowedCompanies, 1, "Asia/Jakarta", "en_US"
        );
        
        List<String> fields = List.of("id", "name", "list_price", "default_code", "qty_available");
        Integer limit = 20;
        
        OdxClientKeywordRequest keywords = new OdxClientKeywordRequest(
            fields, null, limit, 0, requestContext
        );

        // --- Execute and Map ---
        CompletableFuture<OdxServerResponse<List<JsonElement>>> futureRaw = OdxProxy.searchRead(
            "product.product", // <<< New Odoo model
            List.of(), 
            keywords, 
            null, 
            JsonElement.class 
        );

        return futureRaw.thenApply(this::parseAndMapProductResponse);
    }

    private List<Product> parseAndMapProductResponse(OdxServerResponse<List<JsonElement>> response) {
        if (response.getError() != null) {
            throw new RuntimeException("ODX Server Error: " + response.getError().getMessage());
        }

        List<JsonElement> rawResults = response.getResult();
        if (rawResults == null) {
            return List.of();
        }

        return rawResults.stream()
            .map(element -> (JsonObject) element)
            .map(this::createProductFromJsonObject)
            .collect(Collectors.toList());
    }

    private Product createProductFromJsonObject(JsonObject productJson) {
        Product p = new Product();
        
        java.util.function.Function<String, String> safeGetString = key -> {
            JsonElement el = productJson.get(key);
            if (el != null && el instanceof JsonPrimitive) {
                return ((JsonPrimitive) el).getContent();
            }
            return "";
        };

        java.util.function.Function<String, Double> safeGetDouble = key -> {
            try {
                return Double.parseDouble(safeGetString.apply(key));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        };

        p.id = (int) safeGetDouble.apply("id").doubleValue();
        p.name = safeGetString.apply("name");
        p.price = safeGetDouble.apply("list_price");
        p.defaultCode = safeGetString.apply("default_code");
        p.quantity = safeGetDouble.apply("qty_available");

        return p;
    }

    /* POS SESSION */
    public CompletableFuture<Integer> getOpenSessionId() {
        // 1. First, get the active POS Config
        OdxClientRequestContext requestContext = new OdxClientRequestContext(
            List.of(1), 1, "Asia/Jakarta", "en_US"
        );
        
        OdxClientKeywordRequest configKeywords = new OdxClientKeywordRequest(
            List.of("id"), null, 1, 0, requestContext
        );

        return OdxProxy.searchRead("pos.config", 
            List.of(List.of(List.of("active", "=",true))), 
            configKeywords, null, JsonElement.class
        ).thenCompose(configResp -> {
            if (configResp.getError() != null) throw new RuntimeException(configResp.getError().getMessage());
            
            List<JsonElement> configResults = configResp.getResult();
            if (configResults == null || configResults.isEmpty()) throw new RuntimeException("No active POS Config");
            
            int configId = Integer.parseInt(JsonElementKt.getJsonPrimitive(((JsonObject) configResults.get(0)).get("id")).getContent());

            // 2. Now search for an open session for this config
            OdxClientKeywordRequest sessionKeywords = new OdxClientKeywordRequest(
                List.of("id", "state"), "id desc", 1, 0, requestContext
            );

            List<List<Object>> sessionDomain = List.of(List.of(
                List.of("config_id", "=", configId),
                List.of("state", "in", List.of("opened", "opening_control"))
            ));

            return OdxProxy.searchRead("pos.session", sessionDomain, sessionKeywords, null, JsonElement.class);
        }).thenCompose(sessionResp -> {
            List<JsonElement> sessionResults = sessionResp.getResult();
            if (sessionResults == null || sessionResults.isEmpty()) return CompletableFuture.completedFuture(null);

            JsonObject sessionJson = (JsonObject) sessionResults.get(0);
            int sid = Integer.parseInt(JsonElementKt.getJsonPrimitive(sessionJson.get("id")).getContent());
            String state = JsonElementKt.getJsonPrimitive(sessionJson.get("state")).getContent();

            // If in 'opening_control', we trigger the open action
            if (state.equals("opening_control")) {
                OdxClientKeywordRequest sessionKeywords2 = new OdxClientKeywordRequest(
                    null, null, null, null, requestContext
                );

                System.out.println(sessionKeywords2);
                return OdxProxy.callMethod("pos.session", "action_pos_session_open", 
                    List.of(sid), sessionKeywords2, null, JsonElement.class)
                    .thenApply(r -> sid);
            }
            return CompletableFuture.completedFuture(sid);
        });
    }

    public CompletableFuture<Integer> openStore() {
        OdxClientRequestContext requestContext = new OdxClientRequestContext(
            List.of(1), 1, "Asia/Jakarta", "en_US"
        );

        return getOpenSessionId().thenCompose(existingSid -> {
            if (existingSid != null) {
                return CompletableFuture.completedFuture(existingSid);
            }

            // 1. Get Config ID
            OdxClientKeywordRequest configKeywords = new OdxClientKeywordRequest(
                List.of("id"), null, 1, 0, requestContext
            );

            return OdxProxy.searchRead("pos.config", 
                List.of(List.of(List.of("active", "=", true))), 
                configKeywords, null, JsonElement.class
            ).thenCompose(configResp -> {
                if (configResp.getError() != null) throw new RuntimeException(configResp.getError().getMessage());
                
                int configId = Integer.parseInt(JsonElementKt.getJsonPrimitive(
                    ((JsonObject) configResp.getResult().get(0)).get("id")).getContent());

                // 2. Create the session
                Map<String, Object> sessionData = Map.of(
                    "config_id", configId,
                    "name", "POS Session (ODXProxy Java)"
                );

                OdxClientKeywordRequest sessionKeywords3 = new OdxClientKeywordRequest(
                    null, null, null, null, requestContext
                );
                
                return (CompletableFuture<OdxServerResponse<JsonElement>>) OdxProxy.create(
                    "pos.session", List.of(sessionData), sessionKeywords3, null, JsonElement.class);
                    
            }).thenCompose(createResp -> {
                if (createResp.getError() != null) throw new RuntimeException(createResp.getError().getMessage());
                
                // --- FIXED SECTION: Safe ID Extraction ---
                JsonElement result = createResp.getResult();
                int newSid;
                
                if (result instanceof JsonArray) {
                    // If Odoo returns [id]
                    newSid = Integer.parseInt(JsonElementKt.getJsonPrimitive(((JsonArray) result).get(0)).getContent());
                } else {
                    // If Odoo/ODX returns just the id (JsonLiteral/JsonPrimitive)
                    newSid = Integer.parseInt(JsonElementKt.getJsonPrimitive(result).getContent());
                }
                // ------------------------------------------

                // 3. Open the session
                OdxClientKeywordRequest callKeywords = new OdxClientKeywordRequest(null, null, null, null, requestContext);
                return OdxProxy.callMethod("pos.session", "action_pos_session_open", 
                    List.of(newSid), callKeywords, null, JsonElement.class)
                    .thenApply(r -> newSid);
            });
        });
    }

    public CompletableFuture<Boolean> closeStore() {
        OdxClientRequestContext requestContext = new OdxClientRequestContext(
            List.of(1), 1, "Asia/Jakarta", "en_US"
        );

        return getOpenSessionId().<Boolean>thenCompose(sid -> {
            if (sid == null) throw new RuntimeException("No open POS session to close.");

            Map<String, Object> values = Map.of("state", "closing_control");
            OdxClientKeywordRequest sessionKeywords4 = new OdxClientKeywordRequest(
                null, null, null, null, requestContext
            );

            // REMOVE JsonElement.class and change cast to Boolean
            return ((CompletableFuture<OdxServerResponse<Boolean>>) OdxProxy.write(
                    "pos.session", 
                    List.of(sid), 
                    values, 
                    sessionKeywords4, 
                    null // Removed JsonElement.class here
                )).thenCompose(writeResp -> {
                    if (writeResp.getError() != null) throw new RuntimeException(writeResp.getError().getMessage());

                    // callMethod IS generic, so it still needs JsonElement.class
                    return OdxProxy.callMethod("pos.session", "action_pos_session_closing_control", 
                        List.of(sid), sessionKeywords4, null, JsonElement.class);
                }).thenApply(finalResp -> {
                    if (finalResp.getError() != null) throw new RuntimeException(finalResp.getError().getMessage());
                    return true; 
                });
        });
    }

    public CompletableFuture<Integer> addOrderToSession(List<Product> cart) {
        if (cart == null || cart.isEmpty()) {
            return CompletableFuture.failedFuture(new RuntimeException("Cart is empty"));
        }

        OdxClientRequestContext requestContext = new OdxClientRequestContext(
            List.of(1), 1, "Asia/Jakarta", "en_US"
        );

        // 1. Get the session (reusing your existing method)
        return getOpenSessionId().<Integer>thenCompose(sessionId -> {
            if (sessionId == null) {
                throw new RuntimeException("No open POS session. Please OPEN STORE first.");
            }

            // 2. Build the Order Lines [0, 0, {values}]
            double total = 0;
            List<Object> lines = new ArrayList<>();
            
            for (Product item : cart) {
                double price = item.price;
                double qty = 1.0; // Assuming qty 1 for now, or use item.quantity
                double subtotal = price * qty;
                total += subtotal;

                Map<String, Object> lineVals = Map.of(
                    "name", item.name,
                    "product_id", item.id,
                    "price_unit", price,
                    "qty", qty,
                    "price_subtotal", subtotal,
                    "price_subtotal_incl", subtotal
                );
                lines.add(List.of(0, 0, lineVals));
            }

            // 3. Prepare the Payment (Assuming payment_method_id = 1 or fetch as needed)
            // In Odoo POS, usually the first payment method is 'Cash'
            int payMethodId = 1; 
            List<Object> payments = List.of(
                List.of(0, 0, Map.of(
                    "amount", total, 
                    "payment_method_id", payMethodId
                ))
            );

            // 4. Build the full Order object
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("session_id", sessionId);
            orderData.put("name", "POS Order (ODXProxy Java)");
            orderData.put("amount_tax", 0.0);
            orderData.put("amount_total", total);
            orderData.put("amount_paid", total);
            orderData.put("amount_return", 0.0);
            orderData.put("state", "paid");
            orderData.put("lines", lines);
            orderData.put("payment_ids", payments);

            OdxClientKeywordRequest orderKeywords = new OdxClientKeywordRequest(
                null, null, null, null, requestContext
            );

            // 5. Create the order
            return ((CompletableFuture<OdxServerResponse<JsonElement>>) OdxProxy.create(
                    "pos.order", 
                    List.of(orderData), 
                    orderKeywords, 
                    null, 
                    JsonElement.class
                )).thenApply(createResp -> {
                    if (createResp.getError() != null) throw new RuntimeException(createResp.getError().getMessage());

                    // Safe ID extraction logic (handling array vs literal)
                    JsonElement result = createResp.getResult();
                    if (result instanceof JsonArray) {
                        return Integer.parseInt(JsonElementKt.getJsonPrimitive(((JsonArray) result).get(0)).getContent());
                    } else {
                        return Integer.parseInt(JsonElementKt.getJsonPrimitive(result).getContent());
                    }
                });
        });
    }
}