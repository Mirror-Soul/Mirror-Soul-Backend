package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CloneProfileValidator {
    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final Pattern KOREAN_TAG = Pattern.compile("[가-힣 ]+");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern MARKDOWN = Pattern.compile("(^|\\s)(#{1,6}|>|[-*+]\\s|`|\\[.+]\\(.+\\))");

    public GeneratedCloneProfile validate(GeneratedCloneProfile generated) {
        if (generated == null) invalid("Generated clone profile is null");
        String summary = generated.summary() == null ? "" : generated.summary().trim();
        if (!StringUtils.hasText(summary) || summary.length() > SUMMARY_MAX_LENGTH
                || HTML.matcher(summary).find() || MARKDOWN.matcher(summary).find()) {
            invalid("Generated clone summary is invalid");
        }
        if (generated.personalityTags() == null) invalid("Generated personality tags are null");
        List<String> tags = generated.personalityTags().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(tag -> tag.replaceFirst("^#+", ""))
                .filter(StringUtils::hasText)
                .toList();
        if (tags.size() != 3 || tags.stream().distinct().count() != 3
                || tags.stream().anyMatch(tag -> !isAllowedTag(tag))) {
            invalid("Generated personality tags are invalid");
        }
        return new GeneratedCloneProfile(summary, List.copyOf(tags));
    }

    private boolean isAllowedTag(String tag) {
        int length = tag.codePointCount(0, tag.length());
        return length >= 2 && length <= 10 && KOREAN_TAG.matcher(tag).matches();
    }

    private void invalid(String message) {
        throw new CloneProfileGenerationException("INVALID_GENERATED_PROFILE", message, true);
    }
}
