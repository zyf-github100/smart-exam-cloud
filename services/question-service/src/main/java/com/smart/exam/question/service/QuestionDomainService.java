package com.smart.exam.question.service;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.question.dto.CreatePaperRequest;
import com.smart.exam.question.dto.CreateQuestionRequest;
import com.smart.exam.question.model.Paper;
import com.smart.exam.question.model.Question;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionDomainService {

    private final SnowflakeIdGenerator idGenerator;
    private final Map<String, Question> questionStore = new ConcurrentHashMap<>();
    private final Map<String, Paper> paperStore = new ConcurrentHashMap<>();

    public QuestionDomainService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public Question createQuestion(CreateQuestionRequest request, String createdBy) {
        Question question = new Question();
        question.setId(String.valueOf(idGenerator.nextId()));
        question.setType(request.getType());
        question.setStem(request.getStem());
        question.setOptions(request.getOptions());
        question.setAnswer(request.getAnswer());
        question.setDifficulty(request.getDifficulty());
        question.setKnowledgePoint(request.getKnowledgePoint());
        question.setAnalysis(request.getAnalysis());
        question.setCreatedBy(createdBy);
        question.setCreatedAt(LocalDateTime.now());
        questionStore.put(question.getId(), question);
        return question;
    }

    public Collection<Question> listQuestions() {
        return questionStore.values();
    }

    public Question findQuestion(String questionId) {
        Question question = questionStore.get(questionId);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    public Paper createPaper(CreatePaperRequest request, String createdBy) {
        int total = request.getQuestions().stream().mapToInt(q -> q.getScore() == null ? 0 : q.getScore()).sum();
        Paper paper = new Paper();
        paper.setId(String.valueOf(idGenerator.nextId()));
        paper.setName(request.getName());
        paper.setTimeLimitMinutes(request.getTimeLimitMinutes());
        paper.setQuestions(request.getQuestions());
        paper.setTotalScore(total);
        paper.setCreatedBy(createdBy);
        paperStore.put(paper.getId(), paper);
        return paper;
    }

    public Paper findPaper(String paperId) {
        Paper paper = paperStore.get(paperId);
        if (paper == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }
}

