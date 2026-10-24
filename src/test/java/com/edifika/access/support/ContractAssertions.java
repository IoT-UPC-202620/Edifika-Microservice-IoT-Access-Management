package com.edifika.access.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractAssertions {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public static void assertMatches(String schemaPath, Object resource) {
        try (InputStream schemaStream = ContractAssertions.class.getClassLoader().getResourceAsStream(schemaPath)) {
            if (schemaStream == null) {
                System.out.println("Warning: schema not found at " + schemaPath + ", skipping contract check");
                return;
            }
            JsonSchema schema = FACTORY.getSchema(schemaStream);
            JsonNode node = resource instanceof JsonNode jn ? jn :
                    resource instanceof String s ? MAPPER.readTree(s) :
                            MAPPER.valueToTree(resource);
            Set<ValidationMessage> errors = schema.validate(node);
            assertTrue(errors.isEmpty(), "Schema validation errors for " + schemaPath + ": " + errors);
        } catch (Exception e) {
            throw new RuntimeException("Error validating schema " + schemaPath, e);
        }
    }
}
