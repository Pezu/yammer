package com.yammer.repository;

import com.yammer.entity.RecipeComponentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeComponentRepository extends JpaRepository<RecipeComponentEntity, UUID> {

    List<RecipeComponentEntity> findByMenuItemIdOrderBySortOrderAscIdAsc(UUID menuItemId);

    long countByMenuItemId(UUID menuItemId);
}
