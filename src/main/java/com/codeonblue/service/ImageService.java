package com.codeonblue.service;

import com.codeonblue.model.Image;
import com.codeonblue.model.User;
import com.codeonblue.repository.ImageRepository;
import com.codeonblue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class ImageService {

    private static String UPLOAD_ROOT = "upload-dir";

    private final ImageRepository imageRepository;
    private final ResourceLoader resourceLoader;
    private final UserRepository userRepository;
    private final CounterService counterService;
    private final GaugeService gaugeService;
    private final InMemoryMetricRepository inMemoryMetricRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ImageService(ImageRepository imageRepository, ResourceLoader resourceLoader,
                        UserRepository userRepository,
                        CounterService counterService, GaugeService gaugeService,
                        InMemoryMetricRepository inMemoryMetricRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.imageRepository = imageRepository;
        this.resourceLoader = resourceLoader;
        this.userRepository = userRepository;
        this.counterService = counterService;
        this.gaugeService = gaugeService;
        this.inMemoryMetricRepository = inMemoryMetricRepository;
        this.messagingTemplate = messagingTemplate;

        this.counterService.increment("files.uploaded");
        this.gaugeService.submit("files.uploaded.lastBytes", 0);
        this.inMemoryMetricRepository.set(new Metric<Number>("files.uploaded.totalBytes", 0));


    }

    public Page<Image> findPage(Pageable pageable) {
        return imageRepository.findAll(pageable);
    }

    public Resource findOneImage(String filename) {
        return resourceLoader.getResource("file:" + UPLOAD_ROOT + "/" + filename);
    }


    public void createImage(MultipartFile file) throws IOException {

        if(!file.isEmpty()) {
            Files.copy(file.getInputStream(), Paths.get(UPLOAD_ROOT, file.getOriginalFilename()));
            imageRepository.save(new Image(
                    file.getOriginalFilename(),
                    userRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())));
            counterService.increment("files.uploaded");
            gaugeService.submit("files.uploaded.lastBytes", file.getSize());
            inMemoryMetricRepository.increment(new Delta<Number>("files.uploaded.totalBytes", file.getSize()));
            messagingTemplate.convertAndSend("/topic/newImage", file.getOriginalFilename());
        }
    }

    public void deleteImage(String filename) throws IOException {
        final Image byName = imageRepository.findByName(filename);
        imageRepository.delete(byName);
        Files.deleteIfExists(Paths.get(UPLOAD_ROOT, filename));
        messagingTemplate.convertAndSend("/topic/deleteImage", filename);
    }

    // pre load data

    @Bean
    CommandLineRunner setUp(ImageRepository imageRepository,
                            UserRepository userRepository) throws IOException {
        return (args) -> {
            FileSystemUtils.deleteRecursively(new File(UPLOAD_ROOT));

            Files.createDirectory(Paths.get(UPLOAD_ROOT));

            User greg = userRepository.save(new User("greg", "turnquist", "ROLE_ADMIN", "ROLE_USER"));
            User rob = userRepository.save(new User("rob", "winch", "ROLE_USER"));

            FileCopyUtils.copy("Test file", new FileWriter(UPLOAD_ROOT + "/test"));
            imageRepository.save(new Image("test", greg));

            FileCopyUtils.copy("Test file2", new FileWriter(UPLOAD_ROOT + "/test2"));
            imageRepository.save(new Image("test2", greg));

            FileCopyUtils.copy("Test file3", new FileWriter(UPLOAD_ROOT + "/test3"));
            imageRepository.save(new Image("test3", rob));

        };
    }


/*
    @Bean
    CommandLineRunner setUp(ImageRepository imageRepository, ConditionEvaluationReport report) throws IOException {

        return (args) -> {
            FileSystemUtils.deleteRecursively(new File(UPLOAD_ROOT));

            Files.createDirectory(Paths.get(UPLOAD_ROOT));

            FileCopyUtils.copy("Test file", new FileWriter(UPLOAD_ROOT + "/test"));
            imageRepository.save(new Image("test"));

            FileCopyUtils.copy("Test file2", new FileWriter(UPLOAD_ROOT + "/test2"));
            imageRepository.save(new Image("test2"));

            FileCopyUtils.copy("Test file3", new FileWriter(UPLOAD_ROOT + "/test3"));
            imageRepository.save(new Image("test3"));

            report.getConditionAndOutcomesBySource().entrySet().stream()
                    .filter(entry -> entry.getValue().isFullMatch())
                    .forEach(entry ->
                            System.out.println(entry.getKey() + " => Match? " + entry.getValue().isFullMatch())
                    );
        };

    }
*/

}
