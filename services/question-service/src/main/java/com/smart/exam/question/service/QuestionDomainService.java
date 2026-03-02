package com.smart.exam.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.question.dto.CreatePaperRequest;
import com.smart.exam.question.dto.CreateQuestionRequest;
import com.smart.exam.question.entity.PaperEntity;
import com.smart.exam.question.entity.PaperQuestionEntity;
import com.smart.exam.question.entity.QuestionEntity;
import com.smart.exam.question.mapper.PaperMapper;
import com.smart.exam.question.mapper.PaperQuestionMapper;
import com.smart.exam.question.mapper.QuestionMapper;
import com.smart.exam.question.model.Paper;
import com.smart.exam.question.model.PaperQuestion;
import com.smart.exam.question.model.Question;
import com.smart.exam.question.model.QuestionOption;
import com.smart.exam.question.model.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionDomainService {

    private static final Logger log = LoggerFactory.getLogger(QuestionDomainService.class);
    private static final Duration IDEMPOTENT_TTL = Duration.ofSeconds(8);
    private static final Duration QUESTION_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration QUESTION_LIST_TTL = Duration.ofMinutes(5);
    private static final Duration PAPER_DETAIL_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENT_PREFIX = "question:idempotent:";
    private static final String QUESTION_DETAIL_PREFIX = "question:detail:";
    private static final String PAPER_DETAIL_PREFIX = "paper:detail:";
    private static final String QUESTION_LIST_KEY = "question:list";

    private final SnowflakeIdGenerator idGenerator;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public QuestionDomainService(SnowflakeIdGenerator idGenerator,
                                 QuestionMapper questionMapper,
                                 PaperMapper paperMapper,
                                 PaperQuestionMapper paperQuestionMapper,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Question createQuestion(CreateQuestionRequest request, String createdBy) {
        protectFromDuplicateSubmission("create-question", createdBy, request);

        QuestionEntity entity = new QuestionEntity();
        entity.setId(idGenerator.nextId());
        entity.setType(request.getType().name());
        entity.setStem(request.getStem());
        entity.setDifficulty(request.getDifficulty());
        entity.setKnowledgePoint(request.getKnowledgePoint());
        entity.setAnalysis(request.getAnalysis());
        entity.setAnswer(request.getAnswer());
        entity.setCreatedBy(parseLongValue("createdBy", createdBy));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setOptionsJson(writeAsJson(safeOptions(request.getOptions())));
        questionMapper.insert(entity);

        Question question = toQuestion(entity);
        cacheQuestion(question);
        evictQuestionListCache();
        return question;
    }

    public Collection<Question> listQuestions() {
        List<Question> cached = getCachedQuestionList();
        if (cached != null) {
            return cached;
        }

        List<QuestionEntity> entities = questionMapper.selectList(
                Wrappers.lambdaQuery(QuestionEntity.class)
                        .orderByDesc(QuestionEntity::getCreatedAt)
                        .orderByDesc(QuestionEntity::getId)
        );
        List<Question> questions = entities.stream().map(this::toQuestion).toList();
        putCache(QUESTION_LIST_KEY, questions, QUESTION_LIST_TTL);
        return questions;
    }

    public Question findQuestion(String questionId) {
        String cacheKey = QUESTION_DETAIL_PREFIX + questionId;
        Question cached = getCache(cacheKey, Question.class);
        if (cached != null) {
            return cached;
        }

        QuestionEntity entity = questionMapper.selectById(parseLongValue("questionId", questionId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Question not found");
        }

        Question question = toQuestion(entity);
        putCache(cacheKey, question, QUESTION_DETAIL_TTL);
        return question;
    }

    @Transactional
    public Paper createPaper(CreatePaperRequest request, String createdBy) {
        protectFromDuplicateSubmission("create-paper", createdBy, request);

        Set<Long> questionIdSet = request.getQuestions().stream()
                .map(PaperQuestion::getQuestionId)
                .map(questionId -> parseLongValue("questionId", questionId))
                .collect(Collectors.toSet());

        long existingCount = questionMapper.selectCount(
                Wrappers.lambdaQuery(QuestionEntity.class)
                        .in(QuestionEntity::getId, questionIdSet)
        );
        if (existingCount != questionIdSet.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper contains unknown question");
        }

        int total = request.getQuestions().stream()
                .mapToInt(q -> q.getScore() == null ? 0 : q.getScore())
                .sum();

        Long paperId = idGenerator.nextId();
        PaperEntity paperEntity = new PaperEntity();
        paperEntity.setId(paperId);
        paperEntity.setName(request.getName());
        paperEntity.setTotalScore(total);
        paperEntity.setTimeLimitMinutes(request.getTimeLimitMinutes());
        paperEntity.setCreatedBy(parseLongValue("createdBy", createdBy));
        paperEntity.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paperEntity);

        List<PaperQuestionEntity> relationEntities = new ArrayList<>();
        List<PaperQuestion> requestQuestions = request.getQuestions();
        for (int i = 0; i < requestQuestions.size(); i++) {
            PaperQuestion item = requestQuestions.get(i);
            PaperQuestionEntity relation = new PaperQuestionEntity();
            relation.setPaperId(paperId);
            relation.setQuestionId(parseLongValue("questionId", item.getQuestionId()));
            relation.setScore(item.getScore() == null ? 0 : item.getScore());
            relation.setOrderNo(item.getOrderNo() == null ? i + 1 : item.getOrderNo());
            paperQuestionMapper.insert(relation);
            relationEntities.add(relation);
        }

        Paper paper = toPaper(paperEntity, relationEntities);
        putCache(PAPER_DETAIL_PREFIX + paper.getId(), paper, PAPER_DETAIL_TTL);
        return paper;
    }

    public Paper findPaper(String paperId) {
        String cacheKey = PAPER_DETAIL_PREFIX + paperId;
        Paper cached = getCache(cacheKey, Paper.class);
        if (cached != null) {
            return cached;
        }

        Long paperLongId = parseLongValue("paperId", paperId);
        PaperEntity paperEntity = paperMapper.selectById(paperLongId);
        if (paperEntity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Paper not found");
        }

        List<PaperQuestionEntity> relations = paperQuestionMapper.selectList(
                Wrappers.lambdaQuery(PaperQuestionEntity.class)
                        .eq(PaperQuestionEntity::getPaperId, paperLongId)
                        .orderByAsc(PaperQuestionEntity::getOrderNo)
                        .orderByAsc(PaperQuestionEntity::getId)
        );

        Paper paper = toPaper(paperEntity, relations);
        putCache(cacheKey, paper, PAPER_DETAIL_TTL);
        return paper;
    }

    private void protectFromDuplicateSubmission(String action, String userId, Object request) {
        String cacheKey = IDEMPOTENT_PREFIX + action + ":" + userId + ":" + sha256Hex(writeAsJson(request));
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(cacheKey, "1", IDEMPOTENT_TTL);
            if (Boolean.FALSE.equals(success)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate submission");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Skip idempotent check, key={}", cacheKey, e);
        }
    }

    private void evictQuestionListCache() {
        try {
            redisTemplate.delete(QUESTION_LIST_KEY);
        } catch (Exception e) {
            log.warn("Failed to evict question list cache", e);
        }
    }

    private void cacheQuestion(Question question) {
        putCache(QUESTION_DETAIL_PREFIX + question.getId(), question, QUESTION_DETAIL_TTL);
    }

    private Question toQuestion(QuestionEntity entity) {
        Question question = new Question();
        question.setId(String.valueOf(entity.getId()));
        question.setType(QuestionType.valueOf(entity.getType()));
        question.setStem(entity.getStem());
        question.setDifficulty(entity.getDifficulty());
        question.setKnowledgePoint(entity.getKnowledgePoint());
        question.setAnalysis(entity.getAnalysis());
        question.setAnswer(entity.getAnswer());
        question.setCreatedBy(String.valueOf(entity.getCreatedBy()));
        question.setCreatedAt(entity.getCreatedAt());
        question.setOptions(parseQuestionOptions(entity.getOptionsJson()));
        return question;
    }

    private Paper toPaper(PaperEntity paperEntity, List<PaperQuestionEntity> relations) {
        Paper paper = new Paper();
        paper.setId(String.valueOf(paperEntity.getId()));
        paper.setName(paperEntity.getName());
        paper.setTotalScore(paperEntity.getTotalScore());
        paper.setTimeLimitMinutes(paperEntity.getTimeLimitMinutes());
        paper.setCreatedBy(String.valueOf(paperEntity.getCreatedBy()));
        paper.setQuestions(relations.stream().map(this::toPaperQuestion).toList());
        return paper;
    }

    private PaperQuestion toPaperQuestion(PaperQuestionEntity entity) {
        PaperQuestion question = new PaperQuestion();
        question.setQuestionId(String.valueOf(entity.getQuestionId()));
        question.setScore(entity.getScore());
        question.setOrderNo(entity.getOrderNo());
        return question;
    }

    private List<QuestionOption> parseQuestionOptions(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<QuestionOption>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to parse question options");
        }
    }

    private List<QuestionOption> safeOptions(List<QuestionOption> options) {
        if (options == null) {
            return Collections.emptyList();
        }
        return options;
    }

    private <T> T getCache(String cacheKey, Class<T> clazz) {
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedJson)) {
                return null;
            }
            return objectMapper.readValue(cachedJson, clazz);
        } catch (Exception e) {
            log.warn("Failed to read cache, key={}", cacheKey, e);
            return null;
        }
    }

    private List<Question> getCachedQuestionList() {
        try {
            String cachedJson = redisTemplate.opsForValue().get(QUESTION_LIST_KEY);
            if (!StringUtils.hasText(cachedJson)) {
                return null;
            }
            return objectMapper.readValue(cachedJson, new TypeReference<List<Question>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to read question list cache", e);
            return null;
        }
    }

    private void putCache(String cacheKey, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(cacheKey, writeAsJson(value), ttl);
        } catch (Exception e) {
            log.warn("Failed to write cache, key={}", cacheKey, e);
        }
    }

    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to serialize payload");
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 is not available");
        }
    }

    private long parseLongValue(String fieldName, String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName + ": " + rawValue);
        }
    }
}
