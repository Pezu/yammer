package com.yammer.repository;

import com.yammer.entity.OrderPointEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderPointRepository extends JpaRepository<OrderPointEntity, UUID> {

    List<OrderPointEntity> findByLocationIdOrderByName(UUID locationId);

    List<OrderPointEntity> findByLocationIdAndEventIdOrderByName(UUID locationId, UUID eventId);

    /** Order points of one event (for the orders-report order-point filter combo). */
    List<OrderPointEntity> findByEventIdOrderByName(UUID eventId);

    /** Order points at a location, scoped to an event when one is given, else all of the location. */
    default List<OrderPointEntity> findByLocationAndOptionalEventOrderByName(UUID locationId, UUID eventId) {
        return eventId != null
                ? findByLocationIdAndEventIdOrderByName(locationId, eventId)
                : findByLocationIdOrderByName(locationId);
    }

    List<OrderPointEntity> findByLocationIdIn(List<UUID> locationIds);

    /** Order points whose service point is one of the given order points. */
    List<OrderPointEntity> findByServiceOrderPointIdIn(Collection<UUID> serviceOrderPointIds);

    /** Just the ids of every order point — for scope checks that need ids, not whole entities. */
    @Query("select op.id from OrderPointEntity op")
    List<UUID> findAllIds();

    /** Just the ids of the order points under the given client's locations. */
    @Query("select op.id from OrderPointEntity op "
            + "where op.locationId in (select l.id from LocationEntity l where l.clientId = :clientId)")
    List<UUID> findIdsByClientId(@Param("clientId") UUID clientId);

    /** Just the ids of the order points belonging to the given event. */
    @Query("select op.id from OrderPointEntity op where op.eventId = :eventId")
    List<UUID> findIdsByEventId(@Param("eventId") UUID eventId);
}
