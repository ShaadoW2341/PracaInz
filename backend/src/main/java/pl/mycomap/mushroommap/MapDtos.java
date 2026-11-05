package pl.mycomap.mushroommap;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MapDtos {
    private MapDtos() {}
    public record MapInput(@NotBlank(message="Podaj nazwę mapy.") @Size(max=100) String name, @Size(max=1000) String description) {}
    public record MapView(UUID id, String name, String description, boolean isDefault, long observationCount) {}
    public record Species(long id, String polishName, String latinName) {}
    public record ObservationInput(
            @NotNull Long speciesId,
            @NotNull @Min(value=1,message="Liczba sztuk musi być dodatnia.") @Max(100000) Integer quantity,
            @NotNull @PastOrPresent(message="Data nie może być z przyszłości.") LocalDate observedOn,
            @Size(max=2000) String description,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record Observation(UUID id, UUID mapId, long speciesId, String speciesName, String latinName,
                              int quantity, LocalDate observedOn, String description, double latitude, double longitude) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Geometry(String type, List<Double> coordinates) {}
    public record Feature(String type, UUID id, Geometry geometry, java.util.Map<String,Object> properties) {}
    public record GeoJson(String type, List<Feature> features, boolean truncated, int limit) {}
}
