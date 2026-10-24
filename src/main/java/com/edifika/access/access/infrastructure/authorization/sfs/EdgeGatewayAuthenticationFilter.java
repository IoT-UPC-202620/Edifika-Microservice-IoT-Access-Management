package com.edifika.access.access.infrastructure.authorization.sfs;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.interfaces.rest.resources.ErrorResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class EdgeGatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String GATEWAY_REQUEST_ATTR = "edgeGateway";

    private final EdgeGatewayRepository gatewayRepository;
    private final ObjectMapper objectMapper;

    public EdgeGatewayAuthenticationFilter(EdgeGatewayRepository gatewayRepository, ObjectMapper objectMapper) {
        this.gatewayRepository = gatewayRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/edge");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String gatewayCode = request.getHeader("X-Gateway-Id");
        if (gatewayCode == null || gatewayCode.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Falta la cabecera X-Gateway-Id");
            return;
        }

        Optional<EdgeGateway> opt = gatewayRepository.findByGatewayCode(gatewayCode.trim());
        if (opt.isEmpty()) {
            writeError(response, HttpStatus.NOT_FOUND, "Edge Gateway no encontrado con código: " + gatewayCode);
            return;
        }

        EdgeGateway gateway = opt.get();

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Falta token Bearer de autenticación de gateway");
            return;
        }

        String apiKey = authHeader.substring(7).trim();
        if (gateway.isDecommissioned() || !gateway.apiKeyMatches(apiKey)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "API Key inválida o gateway dado de baja");
            return;
        }

        request.setAttribute(GATEWAY_REQUEST_ATTR, gateway);
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResource errorResource = ErrorResource.of(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResource));
    }
}
