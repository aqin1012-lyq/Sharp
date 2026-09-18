package com.sharp.service;

import com.sharp.entity.EmailAccount;
import com.sharp.repository.EmailAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailAccountService {

    private final EmailAccountRepository repository;
    private final EmailParserService parserService;

    public EmailAccountService(EmailAccountRepository repository, EmailParserService parserService) {
        this.repository = repository;
        this.parserService = parserService;
    }

    /** 仅解析预览，不落库。fields 为前端拖拽后的字段顺序，可为空。 */
    public List<EmailAccount> parse(String emailType, String rawData, List<String> fields) {
        return parserService.parseMultiline(emailType, rawData, fields);
    }

    /** 解析并保存。fields 为前端拖拽后的字段顺序，可为空。 */
    public List<EmailAccount> parseAndSave(String emailType, String rawData, List<String> fields) {
        List<EmailAccount> parsed = parserService.parseMultiline(emailType, rawData, fields);
        return repository.saveAll(parsed);
    }

    public Page<EmailAccount> list(String emailType, String keyword, int page, int size) {
        // 前端页码从 1 开始
        int pageIndex = Math.max(page - 1, 0);
        return repository.search(emailType, keyword, PageRequest.of(pageIndex, size));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public EmailAccount update(EmailAccount account) {
        return repository.save(account);
    }
}
