package com.zyh.shortlink.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zyh.shortlink.dto.CreateLinkRequest;
import com.zyh.shortlink.entity.ShortLink;
import com.zyh.shortlink.service.ShortLinkService;
import com.zyh.shortlink.vo.CreateLinkResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class ShortLinkController {

    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @PostMapping("/api/links")
    public CreateLinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
        ShortLink link = service.create(request.getLongUrl(), request.getExpireDays());
        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/" + link.getShortCode())
                .toUriString();
        return new CreateLinkResponse(link.getShortCode(), shortUrl);
    }

    @GetMapping("/api/links")
    public Page<ShortLink> page(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.page(page, size);
    }

    @GetMapping("/api/links/{code}")
    public ResponseEntity<ShortLink> detail(@PathVariable String code) {
        ShortLink link = service.detail(code);
        return link == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                : ResponseEntity.ok(link);
    }

    @GetMapping("/{code:[0-9a-zA-Z]+}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = service.resolve(code);
        if (longUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        service.recordClick(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}
