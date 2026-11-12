package pl.mycomap.sharing;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.mycomap.mushroommap.MapDtos.Page;
import pl.mycomap.security.SessionUser;
import java.util.List;
import java.util.UUID;
import static pl.mycomap.sharing.ShareDtos.*;

@RestController @RequestMapping("/api")
public class ShareController {
    private final ShareService service;
    public ShareController(ShareService service) { this.service=service; }
    @GetMapping("/users/search")
    public List<UserMatch> search(@AuthenticationPrincipal SessionUser user,@RequestParam(defaultValue="") String query) { return service.search(user.id(),query); }
    @GetMapping("/maps/{map}/shares")
    public Page<Share> list(@AuthenticationPrincipal SessionUser user,@PathVariable UUID map,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(user.id(),map,page,size); }
    @PostMapping("/maps/{map}/shares")
    public ResponseEntity<Share> create(@AuthenticationPrincipal SessionUser user,@PathVariable UUID map,@Valid @RequestBody ShareInput input) { return ResponseEntity.status(201).body(service.create(user.id(),map,input)); }
    @PatchMapping("/maps/{map}/shares/{id}")
    public Share update(@AuthenticationPrincipal SessionUser user,@PathVariable UUID map,@PathVariable UUID id,@Valid @RequestBody PrecisionInput input) { return service.update(user.id(),map,id,input); }
    @DeleteMapping("/maps/{map}/shares/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal SessionUser user,@PathVariable UUID map,@PathVariable UUID id) { service.revoke(user.id(),map,id); return ResponseEntity.noContent().build(); }
}
