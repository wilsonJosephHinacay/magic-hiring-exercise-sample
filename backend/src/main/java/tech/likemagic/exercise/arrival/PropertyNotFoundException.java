package tech.likemagic.exercise.arrival;

import java.util.UUID;

public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(UUID propertyId) {
        super("Property not found: " + propertyId);
    }
}
