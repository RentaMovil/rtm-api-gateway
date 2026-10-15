package com.rentamovil.gateway.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

// La petición original no se puede modificar. Este envoltorio permite agregar, reemplazar o quitar
// headers antes de que el gateway la reenvíe al servicio de atrás.
public class MutableHeadersRequest extends HttpServletRequestWrapper {

    private final Map<String, String> added = new LinkedHashMap<>();
    private final List<String> removed = new ArrayList<>();

    public MutableHeadersRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        removed.removeIf(name::equalsIgnoreCase);
        added.keySet().removeIf(name::equalsIgnoreCase);
        added.put(name, value);
    }

    public void removeHeader(String name) {
        added.keySet().removeIf(name::equalsIgnoreCase);
        removed.add(name);
    }

    @Override
    public String getHeader(String name) {
        String value = findAdded(name);
        if (value != null) {
            return value;
        }
        return isRemoved(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = findAdded(name);
        if (value != null) {
            return Collections.enumeration(List.of(value));
        }
        return isRemoved(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        for (String name : Collections.list(super.getHeaderNames())) {
            if (!isRemoved(name) && findAdded(name) == null) {
                names.add(name);
            }
        }
        names.addAll(added.keySet());
        return Collections.enumeration(names);
    }

    private String findAdded(String name) {
        return added.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isRemoved(String name) {
        return removed.stream().anyMatch(name::equalsIgnoreCase);
    }
}
