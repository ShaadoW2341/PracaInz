package pl.mycomap.photo;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.mycomap.common.ApiException;
import pl.mycomap.mushroommap.MapService;
import java.util.*;

@Service @Transactional
public class PhotoService {
    public record Photo(UUID id, int width, int height, int sizeBytes) {}
    public record Image(byte[] bytes, String filename) {}
    private final JdbcTemplate jdbc;
    private final MapService maps;
    private final PhotoProcessor processor;

    public PhotoService(JdbcTemplate jdbc, MapService maps, PhotoProcessor processor) {
        this.jdbc = jdbc; this.maps = maps; this.processor = processor;
    }
    public List<Photo> list(UUID owner, UUID observation) {
        var entry=maps.observation(owner, observation);
        maps.map(owner,entry.mapId());
        return jdbc.query("SELECT id,width,height,size_bytes FROM observation_photos WHERE observation_id=? ORDER BY created_at,id",
                (rs,n) -> new Photo(rs.getObject("id",UUID.class),rs.getInt("width"),rs.getInt("height"),rs.getInt("size_bytes")), observation);
    }
    public List<Photo> upload(UUID owner, UUID observation, List<MultipartFile> files) {
        var entry = maps.observation(owner, observation);
        maps.map(owner, entry.mapId()); // Blokada rodzica chroni limit także przy równoległym wysyłaniu.
        if (files.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"PHOTO_REQUIRED","Wybierz co najmniej jedno zdjęcie.");
        int count = jdbc.queryForObject("SELECT count(*) FROM observation_photos WHERE observation_id=?",Integer.class,observation);
        if (count + files.size() > 5) throw new ApiException(HttpStatus.CONFLICT,"PHOTO_LIMIT","Do znaleziska można dodać maksymalnie 5 zdjęć.");
        var result = new ArrayList<Photo>();
        for (var file : files) {
            var image = processor.process(file);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO observation_photos(id,observation_id,stored_filename,content_type,size_bytes,width,height,image_data,thumbnail_data) VALUES (?,?,?,'image/jpeg',?,?,?,?,?)",
                    id,observation,id+".jpg",image.image().length,image.width(),image.height(),image.image(),image.thumbnail());
            result.add(new Photo(id,image.width(),image.height(),image.image().length));
        }
        return result;
    }
    public Image image(UUID owner, UUID id, boolean thumbnail) {
        String column = thumbnail ? "p.thumbnail_data" : "p.image_data";
        return jdbc.query("SELECT "+column+",p.stored_filename FROM observation_photos p JOIN observations o ON o.id=p.observation_id JOIN mushroom_maps m ON m.id=o.map_id WHERE p.id=? AND m.owner_id=?",
                (rs,n) -> new Image(rs.getBytes(1),rs.getString(2)),id,owner).stream().findFirst().orElseThrow(this::notFound);
    }
    public void delete(UUID owner, UUID id) {
        int deleted = jdbc.update("DELETE FROM observation_photos p USING observations o,mushroom_maps m WHERE p.id=? AND p.observation_id=o.id AND o.map_id=m.id AND m.owner_id=?",id,owner);
        if (deleted == 0) throw notFound();
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Nie znaleziono zdjęcia."); }
}
