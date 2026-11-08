package pl.mycomap.photo;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.mycomap.security.SessionUser;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api")
public class PhotoController {
    private final PhotoService photos;
    public PhotoController(PhotoService photos) { this.photos = photos; }

    @GetMapping("/observations/{id}/photos")
    public List<PhotoService.Photo> list(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id) {
        return photos.list(user.id(),id);
    }
    @PostMapping(value="/observations/{id}/photos", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PhotoService.Photo>> upload(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id, @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(201).body(photos.upload(user.id(),id,files));
    }
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id, @RequestParam(defaultValue="false") boolean thumbnail) {
        var image = photos.image(user.id(),id,thumbnail);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(image.filename()).build().toString())
                .body(image.bytes());
    }
    @DeleteMapping("/photos/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id) {
        photos.delete(user.id(),id); return ResponseEntity.noContent().build();
    }
}
