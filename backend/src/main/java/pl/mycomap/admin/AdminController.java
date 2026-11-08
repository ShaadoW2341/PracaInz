package pl.mycomap.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.mycomap.mushroommap.MapDtos.Page;
import pl.mycomap.security.SessionUser;
import java.util.UUID;
import static pl.mycomap.admin.AdminDtos.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    @GetMapping("/users")
    public Page<UserView> users(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.users(user.id(),search,page,size);
    }
    @PatchMapping("/users/{id}")
    public UserView updateUser(@AuthenticationPrincipal SessionUser user,@PathVariable UUID id,@Valid @RequestBody UserInput input) {
        return service.updateUser(user.id(),id,input);
    }
    @GetMapping("/species")
    public Page<SpeciesView> species(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.species(user.id(),search,page,size);
    }
    @PostMapping("/species")
    public ResponseEntity<SpeciesView> createSpecies(@AuthenticationPrincipal SessionUser user,@Valid @RequestBody SpeciesInput input) {
        return ResponseEntity.status(201).body(service.saveSpecies(user.id(),null,input));
    }
    @PatchMapping("/species/{id}")
    public SpeciesView updateSpecies(@AuthenticationPrincipal SessionUser user,@PathVariable long id,@Valid @RequestBody SpeciesInput input) {
        return service.saveSpecies(user.id(),id,input);
    }
}
