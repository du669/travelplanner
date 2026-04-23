package com.yourcompany.validator.travelplanner.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.validator.travelplanner.config.AmapProperties;
import com.yourcompany.validator.travelplanner.model.Attraction;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AmapPoiService {
    private static final String AMAP_WEB_SERVICE_BASE_URL = "https://restapi.amap.com";

    private static final Map<String, String> CITY_ALIASES = Map.of(
            "shanghai", "上海",
            "tokyo", "东京",
            "chengdu", "成都",
            "beijing", "北京",
            "guangzhou", "广州",
            "shenzhen", "深圳",
            "hangzhou", "杭州"
    );

    private static final Map<String, String> ATTRACTION_ALIASES = Map.ofEntries(
            Map.entry("the bund", "外滩"),
            Map.entry("yu garden", "豫园"),
            Map.entry("shanghai museum", "上海博物馆"),
            Map.entry("tianzifang", "田子坊"),
            Map.entry("tokyo skytree", "东京晴空塔"),
            Map.entry("senso-ji", "浅草寺"),
            Map.entry("ueno park", "上野公园"),
            Map.entry("shibuya", "涩谷"),
            Map.entry("kuanzhai alley", "宽窄巷子"),
            Map.entry("wuhou shrine", "武侯祠"),
            Map.entry("jinli ancient street", "锦里古街"),
            Map.entry("chengdu research base of giant panda breeding", "成都大熊猫繁育研究基地")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AmapProperties amapProperties;
    private final AtomicLong generatedPoiId = new AtomicLong(50000);

    public AmapPoiService(RestClient.Builder restClientBuilder,
                          ObjectMapper objectMapper,
                          AmapProperties amapProperties) {
        this.restClient = restClientBuilder.baseUrl(AMAP_WEB_SERVICE_BASE_URL).build();
        this.objectMapper = objectMapper;
        this.amapProperties = amapProperties;
    }

    public List<Attraction> enrichAttractions(String city, List<Attraction> attractions) {
        if (!StringUtils.hasText(amapProperties.getWebServiceKey()) || attractions == null || attractions.isEmpty()) {
            return attractions == null ? List.of() : attractions;
        }

        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction attraction : attractions) {
            Attraction enriched = enrichAttraction(city, attraction);
            if (enriched == null) {
                continue;
            }
            deduped.putIfAbsent(normalizeName(enriched.name()), enriched);
        }
        return new ArrayList<>(deduped.values());
    }

    public List<Attraction> searchSupplementalAttractions(String city, List<String> interests, Set<String> excludedNames, int limit) {
        if (!StringUtils.hasText(amapProperties.getWebServiceKey()) || !StringUtils.hasText(city) || limit <= 0) {
            return List.of();
        }

        String region = localizeCity(city);
        List<String> keywords = buildSearchKeywords(region, interests);
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        Set<String> excluded = excludedNames == null ? Set.of() : excludedNames;

        for (String keyword : keywords) {
            for (AmapPoi poi : searchPois(keyword, region, Math.max(limit * 3, 12))) {
                String normalizedName = normalizeName(poi.name());
                if (!StringUtils.hasText(normalizedName) || excluded.contains(normalizedName) || deduped.containsKey(normalizedName)) {
                    continue;
                }
                if (!belongsToCity(poi, region)) {
                    continue;
                }
                Attraction attraction = toAttraction(region, poi);
                if (attraction != null) {
                    deduped.put(normalizedName, attraction);
                }
                if (deduped.size() >= limit) {
                    return new ArrayList<>(deduped.values());
                }
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private Attraction enrichAttraction(String city, Attraction attraction) {
        if (attraction == null || !StringUtils.hasText(attraction.name())) {
            return attraction;
        }

        String region = localizeCity(StringUtils.hasText(city) ? city : attraction.city());
        List<String> queries = buildAttractionQueries(attraction.name());
        for (String query : queries) {
            List<AmapPoi> pois = searchPois(query, region, 8);
            AmapPoi matchedPoi = pois.stream().filter(poi -> belongsToCity(poi, region)).findFirst().orElse(null);
            if (matchedPoi == null) {
                continue;
            }

            double[] lngLat = parseLocation(bestLocation(matchedPoi));
            if (lngLat == null) {
                continue;
            }

            return new Attraction(
                    attraction.id(),
                    attraction.name(),
                    StringUtils.hasText(attraction.city()) ? attraction.city() : region,
                    attraction.category(),
                    StringUtils.hasText(attraction.description()) ? attraction.description() : defaultText(matchedPoi.address(), "高德地点搜索补充景点信息"),
                    lngLat[1],
                    lngLat[0],
                    attraction.rating(),
                    attraction.suggestedHours(),
                    attraction.tags() == null || attraction.tags().isEmpty() ? List.of("高德补全") : attraction.tags(),
                    attraction.imageUrls() != null && !attraction.imageUrls().isEmpty()
                            ? attraction.imageUrls()
                            : extractPhotoUrls(matchedPoi.photos())
            );
        }

        return shouldKeepWithoutMatch(region, attraction) ? attraction : null;
    }

    private boolean shouldKeepWithoutMatch(String city, Attraction attraction) {
        if (attraction == null) {
            return false;
        }
        if (isGeneratedAttraction(attraction)) {
            return false;
        }
        return normalizeCityToken(city).equals(normalizeCityToken(attraction.city()));
    }

    private boolean isGeneratedAttraction(Attraction attraction) {
        return attraction.id() != null && attraction.id() >= 10000;
    }

    private Attraction toAttraction(String city, AmapPoi poi) {
        if (poi == null || !StringUtils.hasText(poi.name()) || !StringUtils.hasText(bestLocation(poi))) {
            return null;
        }
        double[] lngLat = parseLocation(bestLocation(poi));
        if (lngLat == null) {
            return null;
        }

        String category = mapPoiTypeToCategory(poi.type());
        List<String> tags = new ArrayList<>();
        tags.add("高德推荐");
        tags.add(category);

        return new Attraction(
                generatedPoiId.getAndIncrement(),
                poi.name(),
                StringUtils.hasText(city) ? city : poi.cityname(),
                category,
                defaultText(poi.address(), "来自高德地点搜索的补充景点"),
                lngLat[1],
                lngLat[0],
                parseRating(poi.business()),
                estimateSuggestedHours(category),
                tags,
                extractPhotoUrls(poi.photos())
        );
    }

    private List<AmapPoi> searchPois(String keywords, String city, int pageSize) {
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v5/place/text")
                            .queryParam("key", amapProperties.getWebServiceKey())
                            .queryParam("keywords", keywords)
                            .queryParam("region", city)
                            .queryParam("city_limit", true)
                            .queryParam("show_fields", "business,navi,photos")
                            .queryParam("page_size", Math.min(25, Math.max(1, pageSize)))
                            .queryParam("page_num", 1)
                            .build())
                    .retrieve()
                    .body(String.class);
            AmapPlaceResponse response = objectMapper.readValue(responseBody, AmapPlaceResponse.class);
            if (response == null || !"1".equals(response.status()) || response.pois() == null) {
                return List.of();
            }
            return response.pois();
        } catch (RestClientResponseException exception) {
            return List.of();
        } catch (RestClientException exception) {
            return List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> buildSearchKeywords(String city, List<String> interests) {
        List<String> keywords = new ArrayList<>();
        keywords.add(city + "热门景点");
        keywords.add(city + "景点");
        keywords.add(city + "必去景点");

        if (interests != null) {
            for (String interest : interests) {
                switch ((interest == null ? "" : interest).toLowerCase(Locale.ROOT)) {
                    case "culture" -> {
                        keywords.add(city + "博物馆");
                        keywords.add(city + "历史景点");
                    }
                    case "food" -> keywords.add(city + "美食街");
                    case "history" -> keywords.add(city + "古迹");
                    case "nature" -> keywords.add(city + "公园");
                    case "walk", "citywalk" -> keywords.add(city + "步行街");
                    case "view" -> keywords.add(city + "观景台");
                    default -> {
                    }
                }
            }
        }

        return new ArrayList<>(new LinkedHashSet<>(keywords));
    }

    private List<String> buildAttractionQueries(String attractionName) {
        String normalized = normalizeName(attractionName);
        List<String> queries = new ArrayList<>();
        queries.add(attractionName);
        String alias = ATTRACTION_ALIASES.get(normalized);
        if (StringUtils.hasText(alias)) {
            queries.add(alias);
        }
        return new ArrayList<>(new LinkedHashSet<>(queries));
    }

    private boolean belongsToCity(AmapPoi poi, String city) {
        if (!StringUtils.hasText(city) || poi == null) {
            return true;
        }
        String normalizedCity = normalizeCityToken(city);
        String normalizedPoiCity = normalizeCityToken(poi.cityname());
        String normalizedPoiProvince = normalizeCityToken(poi.pname());
        return normalizedPoiCity.equals(normalizedCity)
                || normalizedPoiCity.contains(normalizedCity)
                || normalizedCity.contains(normalizedPoiCity)
                || normalizedPoiProvince.equals(normalizedCity);
    }

    private String localizeCity(String city) {
        if (!StringUtils.hasText(city)) {
            return "";
        }
        String normalized = city.trim().toLowerCase(Locale.ROOT);
        return CITY_ALIASES.getOrDefault(normalized, city.trim());
    }

    private String normalizeCityToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("市", "")
                .replace("省", "")
                .replace("特别行政区", "")
                .replace("province", "")
                .replace("city", "");

        return CITY_ALIASES.getOrDefault(normalized, normalized)
                .replace("上海", "上海")
                .replace("北京", "北京")
                .replace("成都", "成都")
                .replace("广州", "广州")
                .replace("深圳", "深圳")
                .replace("杭州", "杭州")
                .replace("东京", "东京");
    }

    private String mapPoiTypeToCategory(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (value.contains("博物馆")) return "museum";
        if (value.contains("风景名胜") || value.contains("景点")) return "sight";
        if (value.contains("公园")) return "park";
        if (value.contains("步行街")) return "citywalk";
        if (value.contains("美食")) return "food";
        if (value.contains("古迹")) return "history";
        if (value.contains("观景")) return "view";
        return "sight";
    }

    private int estimateSuggestedHours(String category) {
        return switch (category) {
            case "museum" -> 3;
            case "park", "citywalk", "food" -> 2;
            default -> 2;
        };
    }

    private double parseRating(AmapBusiness business) {
        if (business == null || !StringUtils.hasText(business.rating())) {
            return 4.5;
        }
        try {
            return Double.parseDouble(business.rating());
        } catch (NumberFormatException exception) {
            return 4.5;
        }
    }

    private String bestLocation(AmapPoi poi) {
        if (poi == null) {
            return null;
        }
        if (poi.navi() != null && StringUtils.hasText(poi.navi().entrLocation())) {
            return poi.navi().entrLocation();
        }
        return poi.location();
    }

    private List<String> extractPhotoUrls(List<AmapPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .map(AmapPhoto::url)
                .filter(StringUtils::hasText)
                .map(this::normalizePhotoUrl)
                .limit(3)
                .toList();
    }

    private String normalizePhotoUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    private double[] parseLocation(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapPlaceResponse(
            String status,
            List<AmapPoi> pois
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapPoi(
            String name,
            String location,
            String address,
            String type,
            String cityname,
            String pname,
            AmapBusiness business,
            AmapNavi navi,
            List<AmapPhoto> photos
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapBusiness(
            String rating
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapNavi(
            String entrLocation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapPhoto(
            String title,
            String url
    ) {
    }
}
