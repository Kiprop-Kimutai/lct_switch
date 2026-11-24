package compas.device;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

@RepositoryRestResource(collectionResourceRel = "people", path = "people")
public interface DeviceRepository extends MongoRepository<Device, String> {
    @RestResource(exported = false)
    Optional<Device> findFirstByMacAddress(String macAddress);
}
