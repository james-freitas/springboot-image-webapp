package com.codeonblue.repository;

import com.codeonblue.model.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.awt.print.Pageable;

public interface ImageRepository extends PagingAndSortingRepository<Image, Long> {

    public Image findByName(String name);

}
