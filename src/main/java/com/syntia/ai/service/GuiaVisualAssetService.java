package com.syntia.ai.service;

import com.syntia.ai.model.VisualAsset;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.repository.VisualAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GuiaVisualAssetService {

    private static final int MAX_REFERENCES_TO_RESOLVE = 18;
    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ICON_LINK = Pattern.compile(
            "<link[^>]+rel=[\"'][^\"']*(?:icon|apple-touch-icon)[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private final VisualAssetRepository visualAssetRepository;
    private final HttpClient httpClient;

    public GuiaVisualAssetService(VisualAssetRepository visualAssetRepository) {
        this.visualAssetRepository = visualAssetRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public GuiaSubvencionDTO enriquecerGuia(GuiaSubvencionDTO guia) {
        if (guia == null) return null;

        ensureVisualReferences(guia);

        if (guia.getVisualIdentity() != null) {
            VisualAsset asset = resolve(
                    guia.getVisualIdentity().getEntity(),
                    guia.getVisualIdentity().getKind(),
                    guia.getVisualIdentity().getDomain(),
                    guia.getVisualIdentity().getOfficialUrl()
            );
            apply(guia.getVisualIdentity(), asset);
        }

        List<GuiaSubvencionDTO.VisualReference> references = guia.getVisualReferences();
        if (references == null) return guia;

        int resolved = 0;
        for (GuiaSubvencionDTO.VisualReference ref : references) {
            if (ref == null || resolved >= MAX_REFERENCES_TO_RESOLVE) continue;
            VisualAsset asset = resolve(firstNonBlank(ref.getEntity(), ref.getSearchHint()), ref.getVisualType(), ref.getDomain(), ref.getOfficialUrl());
            apply(ref, asset);
            resolved++;
        }

        return guia;
    }

    private void ensureVisualReferences(GuiaSubvencionDTO guia) {
        List<GuiaSubvencionDTO.VisualReference> refs = guia.getVisualReferences();
        if (refs == null) {
            refs = new ArrayList<>();
            guia.setVisualReferences(refs);
        }

        if (guia.getVisualIdentity() == null) {
            GuiaSubvencionDTO.OfficialInfo official = guia.getOfficial();
            GuiaSubvencionDTO.GrantSummary summary = guia.getGrantSummary();
            String entity = firstNonBlank(
                    official != null ? official.getOrganismName() : null,
                    summary != null ? summary.getOrganism() : null
            );
            String url = firstNonBlank(
                    official != null ? official.getOfficialUrl() : null,
                    summary != null ? summary.getOfficialLink() : null
            );
            String domain = firstNonBlank(
                    official != null ? official.getPortalDomain() : null,
                    domainFromUrl(url)
            );
            if (entity != null || domain != null) {
                guia.setVisualIdentity(GuiaSubvencionDTO.VisualIdentity.builder()
                        .entity(entity != null ? entity : domain)
                        .domain(domain)
                        .kind(inferVisualType(entity + " " + domain))
                        .officialUrl(url)
                        .build());
            }
        }

        addOfficialReference(guia, refs);
        addDocumentReferences(guia, refs);
        addRequirementReferences(guia, refs);
        addApplicationReferences(guia, refs);
        addStepReferences(guia, refs);
    }

    private void addOfficialReference(GuiaSubvencionDTO guia, List<GuiaSubvencionDTO.VisualReference> refs) {
        GuiaSubvencionDTO.OfficialInfo official = guia.getOfficial();
        GuiaSubvencionDTO.GrantSummary summary = guia.getGrantSummary();
        String entity = firstNonBlank(
                official != null ? official.getOrganismName() : null,
                summary != null ? summary.getOrganism() : null
        );
        String url = firstNonBlank(
                official != null ? official.getOfficialUrl() : null,
                summary != null ? summary.getOfficialLink() : null
        );
        String domain = firstNonBlank(
                official != null ? official.getPortalDomain() : null,
                domainFromUrl(url)
        );
        addReferenceIfMissing(refs, "resumen", entity, inferVisualType(entity + " " + domain), domain, url, entity);
        addReferenceIfMissing(refs, "legal", entity, "legal_source", domain, url, "Fuente oficial");
    }

    private void addDocumentReferences(GuiaSubvencionDTO guia, List<GuiaSubvencionDTO.VisualReference> refs) {
        if (guia.getStructuredDocuments() == null) return;
        for (GuiaSubvencionDTO.StructuredDocument doc : guia.getStructuredDocuments()) {
            String text = join(doc.getName(), doc.getDescription(), doc.getType(), doc.getEntity());
            VisualHint hint = inferHint(text);
            addReferenceIfMissing(refs, "documentos",
                    firstNonBlank(doc.getEntity(), hint.entity()),
                    firstNonBlank(doc.getVisualType(), hint.visualType(), doc.getType()),
                    hint.domain(), hint.officialUrl(), doc.getName());
        }
    }

    private void addRequirementReferences(GuiaSubvencionDTO guia, List<GuiaSubvencionDTO.VisualReference> refs) {
        if (guia.getUniversalRequirementsLgsArt13() == null) return;
        for (String item : guia.getUniversalRequirementsLgsArt13()) {
            VisualHint hint = inferHint(item);
            addReferenceIfMissing(refs, "requisitos", hint.entity(), hint.visualType(), hint.domain(), hint.officialUrl(), item);
        }
    }

    private void addApplicationReferences(GuiaSubvencionDTO guia, List<GuiaSubvencionDTO.VisualReference> refs) {
        if (guia.getApplicationMethods() == null) return;
        for (GuiaSubvencionDTO.ApplicationMethod method : guia.getApplicationMethods()) {
            String text = join(method.getEntity(), method.getDescription(), method.getOfficialPortal(), method.getMethod());
            VisualHint hint = inferHint(text);
            String url = firstNonBlank(method.getOfficialPortal(), hint.officialUrl());
            addReferenceIfMissing(refs, "solicitud",
                    firstNonBlank(method.getEntity(), hint.entity()),
                    firstNonBlank(method.getVisualType(), hint.visualType(), "e_office"),
                    firstNonBlank(domainFromUrl(url), hint.domain()), url, method.getMethod());
        }
    }

    private void addStepReferences(GuiaSubvencionDTO guia, List<GuiaSubvencionDTO.VisualReference> refs) {
        if (guia.getWorkflows() == null || guia.getWorkflows().isEmpty()) return;
        List<GuiaSubvencionDTO.WorkflowStep> steps = guia.getWorkflows().get(0).getSteps();
        if (steps == null) return;

        for (GuiaSubvencionDTO.WorkflowStep step : steps) {
            String text = join(step.getPortalEntity(), step.getTitle(), step.getDescription(), step.getUserAction(), step.getOfficialLink());
            VisualHint hint = inferHint(text);
            String url = firstNonBlank(step.getOfficialLink(), hint.officialUrl());
            addReferenceIfMissing(refs, "pasos",
                    firstNonBlank(step.getPortalEntity(), hint.entity()),
                    firstNonBlank(step.getVisualType(), hint.visualType(), step.getActionType()),
                    firstNonBlank(domainFromUrl(url), hint.domain()), url, step.getTitle());
        }
    }

    private void addReferenceIfMissing(List<GuiaSubvencionDTO.VisualReference> refs, String section, String entity,
                                       String visualType, String domain, String officialUrl, String label) {
        if (entity == null && domain == null && label == null) return;
        String key = assetKey(firstNonBlank(entity, label, domain), visualType, domain);
        boolean exists = refs.stream().filter(Objects::nonNull).anyMatch(ref ->
                section.equalsIgnoreCase(nullToEmpty(ref.getSection()))
                        && assetKey(firstNonBlank(ref.getEntity(), ref.getLabel(), ref.getDomain()),
                        ref.getVisualType(), ref.getDomain()).equals(key));
        if (exists) return;

        refs.add(GuiaSubvencionDTO.VisualReference.builder()
                .section(section)
                .entity(firstNonBlank(entity, label, domain))
                .visualType(firstNonBlank(visualType, "generic_document"))
                .domain(domain)
                .officialUrl(officialUrl)
                .label(label)
                .build());
    }

    private VisualAsset resolve(String entity, String visualType, String domain, String officialUrl) {
        String normalizedEntity = normalize(firstNonBlank(entity, domain, visualType, "asset"));
        String normalizedType = normalize(firstNonBlank(visualType, "other"));
        String normalizedDomain = normalize(firstNonBlank(domain, domainFromUrl(officialUrl), ""));
        String assetKey = assetKey(normalizedEntity, normalizedType, normalizedDomain);

        Optional<VisualAsset> existing = visualAssetRepository.findByAssetKey(assetKey);
        if (existing.isPresent()) {
            VisualAsset asset = existing.get();
            asset.setUsageCount((asset.getUsageCount() == null ? 0 : asset.getUsageCount()) + 1);
            asset.setLastUsedAt(LocalDateTime.now());
            return asset;
        }

        VisualCandidate candidate = discoverCandidate(entity, visualType, domain, officialUrl);
        VisualAsset asset = VisualAsset.builder()
                .assetKey(assetKey)
                .entityName(limit(firstNonBlank(entity, domain, visualType, "Referencia visual"), 280))
                .normalizedEntity(normalizedEntity)
                .visualType(limit(firstNonBlank(visualType, "other"), 70))
                .domain(limit(firstNonBlank(domain, domainFromUrl(officialUrl)), 240))
                .imageUrl(candidate.imageUrl())
                .sourceUrl(candidate.sourceUrl())
                .sourceType(candidate.sourceType())
                .confidence(candidate.confidence())
                .status(candidate.status())
                .usageCount(1)
                .lastUsedAt(LocalDateTime.now())
                .build();
        return visualAssetRepository.save(asset);
    }

    private VisualCandidate discoverCandidate(String entity, String visualType, String domain, String officialUrl) {
        String resolvedDomain = firstNonBlank(domain, domainFromUrl(officialUrl));
        String startUrl = firstNonBlank(officialUrl, resolvedDomain != null ? "https://" + resolvedDomain : null);

        if (startUrl != null) {
            Optional<String> metadataImage = fetchMetadataImage(startUrl);
            if (metadataImage.isPresent()) {
                return new VisualCandidate(metadataImage.get(), startUrl, "official_metadata", "auto", 92);
            }
        }

        if (resolvedDomain != null) {
            String clearbit = "https://logo.clearbit.com/" + resolvedDomain;
            if (looksReachable(clearbit)) {
                return new VisualCandidate(clearbit, "https://" + resolvedDomain, "clearbit_domain_logo", "auto", 72);
            }

            String googleFavicon = "https://www.google.com/s2/favicons?domain=" + resolvedDomain + "&sz=128";
            return new VisualCandidate(googleFavicon, "https://" + resolvedDomain, "google_favicon", "auto", 58);
        }

        return new VisualCandidate(null, null, "semantic_fallback", "fallback", 35);
    }

    private Optional<String> fetchMetadataImage(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "SyntiaBot/1.0 visual-assets")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400 || response.body() == null) {
                return Optional.empty();
            }

            String body = response.body();
            Optional<String> image = firstMatch(body, OG_IMAGE).or(() -> firstMatch(body, ICON_LINK));
            return image.map(value -> absoluteUrl(uri, value));
        } catch (Exception e) {
            log.debug("No se pudo descubrir metadata visual en {}: {}", rawUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean looksReachable(String rawUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(rawUrl))
                    .timeout(Duration.ofSeconds(2))
                    .header("User-Agent", "SyntiaBot/1.0 visual-assets")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<String> firstMatch(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }

    private String absoluteUrl(URI base, String value) {
        if (value == null || value.isBlank()) return value;
        try {
            return base.resolve(value.trim()).toString();
        } catch (Exception e) {
            return value;
        }
    }

    private void apply(GuiaSubvencionDTO.VisualIdentity identity, VisualAsset asset) {
        if (identity == null || asset == null) return;
        identity.setVisualAssetUrl(asset.getImageUrl());
        identity.setVisualAssetSource(asset.getSourceType());
        identity.setVisualAssetStatus(asset.getStatus());
        identity.setVisualAssetConfidence(asset.getConfidence());
    }

    private void apply(GuiaSubvencionDTO.VisualReference ref, VisualAsset asset) {
        if (ref == null || asset == null) return;
        ref.setVisualAssetUrl(asset.getImageUrl());
        ref.setVisualAssetSource(asset.getSourceType());
        ref.setVisualAssetStatus(asset.getStatus());
        ref.setVisualAssetConfidence(asset.getConfidence());
    }

    private VisualHint inferHint(String value) {
        String text = normalize(value);
        if (containsAny(text, "aeat", "agencia tributaria", "hacienda", "tributaria")) {
            return new VisualHint("AEAT", "tax_agency", "sede.agenciatributaria.gob.es", "https://sede.agenciatributaria.gob.es/");
        }
        if (containsAny(text, "tgss", "seguridad social", "cotizacion", "cotización")) {
            return new VisualHint("Seguridad Social", "social_security", "sede.seg-social.gob.es", "https://sede.seg-social.gob.es/");
        }
        if (containsAny(text, "comunidad de madrid", "madrid.org", "comunidad.madrid")) {
            return new VisualHint("Comunidad de Madrid", "regional_government", "comunidad.madrid", "https://www.comunidad.madrid/");
        }
        if (containsAny(text, "fnmt", "certificado digital", "firma electronica", "firma electrónica", "autofirma", "clave", "cl@ve")) {
            return new VisualHint("Certificado digital", "digital_certificate", "sede.fnmt.gob.es", "https://www.sede.fnmt.gob.es/certificados");
        }
        if (containsAny(text, "bdns", "infosubvenciones")) {
            return new VisualHint("BDNS", "legal_source", "infosubvenciones.es", "https://www.infosubvenciones.es/");
        }
        if (containsAny(text, "dni", "nie", "nif", "cif", "identificativo")) {
            return new VisualHint("DNI/NIE", "identity_document", null, null);
        }
        if (containsAny(text, "dinero", "importe", "pago", "subvencion", "subvención", "ayuda", "financiacion", "financiación")) {
            return new VisualHint("Financiacion", "money", null, null);
        }
        return new VisualHint(null, inferVisualType(value), null, null);
    }

    private String inferVisualType(String value) {
        String text = normalize(value);
        if (containsAny(text, "aeat", "tributaria", "hacienda")) return "tax_agency";
        if (containsAny(text, "seguridad social", "tgss", "cotizacion")) return "social_security";
        if (containsAny(text, "comunidad", "ayuntamiento", "consejeria", "ministerio")) return "regional_government";
        if (containsAny(text, "certificado", "firma", "clave", "fnmt")) return "digital_certificate";
        if (containsAny(text, "dni", "nie", "nif", "cif")) return "identity_document";
        if (containsAny(text, "sede", "portal", "registro")) return "e_office";
        if (containsAny(text, "legal", "bases", "normativa", "bdns")) return "legal_source";
        if (containsAny(text, "dinero", "pago", "ayuda", "importe", "financiacion")) return "money";
        return "generic_document";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(normalize(value))) return true;
        }
        return false;
    }

    private String assetKey(String entity, String visualType, String domain) {
        return limit(normalize(firstNonBlank(entity, "asset")), 180) + "|" +
                limit(normalize(firstNonBlank(visualType, "other")), 80) + "|" +
                limit(normalize(firstNonBlank(domain, "")), 150);
    }

    private String domainFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return URI.create(url).getHost() != null
                    ? URI.create(url).getHost().replaceFirst("^www\\.", "")
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String join(String... values) {
        return String.join(" ", Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record VisualCandidate(String imageUrl, String sourceUrl, String sourceType, String status, Integer confidence) {}
    private record VisualHint(String entity, String visualType, String domain, String officialUrl) {}
}
