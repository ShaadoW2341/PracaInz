package pl.mycomap.mushroommap;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.mycomap.security.SessionUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static pl.mycomap.mushroommap.MapDtos.*;

@RestController @RequestMapping("/api")
public class MapController {
    private final MapService service;
    public MapController(MapService service) { this.service=service; }
    @GetMapping("/species") public List<Species> species() { return service.species(); }
    @GetMapping("/maps") public Page<MapView> maps(@AuthenticationPrincipal SessionUser user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return service.maps(user.id(),page,size); }
    @PostMapping("/maps") public ResponseEntity<MapView> create(@AuthenticationPrincipal SessionUser user,@Valid @RequestBody MapInput input) { return ResponseEntity.status(201).body(service.createMap(user.id(),input)); }
    @GetMapping("/maps/{id}") public MapView map(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id) { return service.map(user.id(),id); }
    @PatchMapping("/maps/{id}") public MapView update(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@Valid @RequestBody MapInput input) { return service.updateMap(user.id(),id,input); }
    @DeleteMapping("/maps/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id) { service.deleteMap(user.id(),id); return ResponseEntity.noContent().build(); }
    @GetMapping("/maps/{id}/observations") public Page<Observation> observations(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@RequestParam(required=false) Long speciesId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String bbox,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.observations(user.id(),id,speciesId,from,to,bbox,page,size); }
    @GetMapping("/maps/{id}/observations/geojson") public GeoJson geojson(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@RequestParam(required=false) Long speciesId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String bbox) { return service.geoJson(user.id(),id,speciesId,from,to,bbox); }
    @PostMapping("/maps/{id}/observations") public ResponseEntity<Observation> add(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@Valid @RequestBody ObservationInput input) { return ResponseEntity.status(201).body(service.saveObservation(user.id(),id,null,input)); }
    @GetMapping("/observations/{id}") public Observation observation(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id) { return service.observation(user.id(),id); }
    @PatchMapping("/observations/{id}") public Observation edit(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@Valid @RequestBody ObservationInput input) { return service.updateObservation(user.id(),id,input); }
    @DeleteMapping("/observations/{id}") public ResponseEntity<Void> remove(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id) { service.deleteObservation(user.id(),id); return ResponseEntity.noContent().build(); }
}
