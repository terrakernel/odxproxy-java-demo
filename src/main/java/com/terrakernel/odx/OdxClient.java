package com.terrakernel.odx;

import io.odxproxy.OdxProxy;
import io.odxproxy.client.OdxProxyClientInfo;
import io.odxproxy.model.OdxClientKeywordRequest;
import io.odxproxy.model.OdxInstanceInfo;
import io.odxproxy.model.OdxServerResponse;
import io.odxproxy.model.OdxClientRequestContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import kotlinx.serialization.json.JsonElement;
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
}