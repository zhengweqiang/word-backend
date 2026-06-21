package com.example.words.service;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.GenerateReadingRequest;
import com.example.words.dto.GenerateWordDetailsRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiPromptService {

    public List<AiChatMessageRequest> buildReadingMessages(GenerateReadingRequest request) {
        String systemPrompt = "你是一名英语教学内容助手，请输出适合英语学习的阅读短文。";
        String userPrompt = """
                请根据以下要求生成一篇英语阅读短文：
                主题：%s
                字数：约 %d 词
                难度：%s
                输出格式：
                标题：...
                正文：...
                """.formatted(
                request.getTopic().trim(),
                request.getWordCount(),
                request.getDifficulty().trim()
        );

        return List.of(
                new AiChatMessageRequest("system", systemPrompt),
                new AiChatMessageRequest("user", userPrompt)
        );
    }

    public List<AiChatMessageRequest> buildWordDetailsMessages(GenerateWordDetailsRequest request) {
        String systemPrompt = """
                你是一名严谨的英语词汇助手。
                你的任务是根据给定英文单词，生成适合英语学习产品录入的词条信息。
                只返回 JSON 对象，不要输出 Markdown、解释文字、代码块或额外说明。
                如果某个字段不确定，返回空字符串。
                """;
        String userPrompt = """
                请为英文单词 "%s" 生成词条详情，并严格返回以下 JSON 结构：
                {
                  "word": "%s",
                  "translation": "中文释义，简洁自然",
                  "partOfSpeech": "常见词性，如 noun / verb / adjective",
                  "phonetic": "常见国际音标，格式如 /.../",
                  "definition": "简洁英文释义",
                  "exampleSentence": "自然、简短、适合学习的英文例句"
                }
                要求：
                1. word 字段返回规范拼写。
                2. translation 返回最常见、最适合学习者理解的中文释义。
                3. definition 用简洁英文解释。
                4. exampleSentence 使用该单词本身，避免过长。
                5. 只返回一个 JSON 对象。
                """.formatted(
                request.getWord().trim(),
                request.getWord().trim()
        );

        return List.of(
                new AiChatMessageRequest("system", systemPrompt),
                new AiChatMessageRequest("user", userPrompt)
        );
    }

    public List<AiChatMessageRequest> buildWordDetailsV2Messages(GenerateWordDetailsRequest request) {
        String word = request.getWord().trim();
        String systemPrompt = """
                你是一名严谨的英语词汇结构化数据助手。
                你要为英语学习系统生成符合指定 JSON Schema 的词条数据。
                只返回一个 JSON 对象，不要输出 Markdown、解释、代码块或额外说明。
                如果不确定字段内容，返回空字符串或空数组，但保持 JSON 结构合法。
                """;
        String userPrompt = """
                请为英文单词 "%s" 生成符合 MetaWordEntryDtoV2 结构的 JSON：
                {
                  "word": "%s",
                  "phonetic": {
                    "uk": "/英式音标/",
                    "us": "/美式音标/"
                  },
                  "syllableDetail": {
                    "segments": [
                      {
                        "text": "按拼写顺序拆分的音节",
                        "ukPhonetic": "/英式音节音标/",
                        "usPhonetic": "/美式音节音标/",
                        "ukAudioUrl": null,
                        "usAudioUrl": null
                      }
                    ]
                  },
                  "partOfSpeech": [
                    {
                      "pos": "noun",
                      "definitions": [
                        {
                          "definition": "简洁英文释义",
                          "translation": "对应中文释义",
                          "exampleSentences": [
                            {
                              "sentence": "自然、简洁、适合学习的英文例句",
                              "translation": "该例句的中文翻译"
                            }
                          ]
                        }
                      ],
                      "synonyms": [],
                      "antonyms": []
                    }
                  ],
                  "difficulty": 2
                }
                要求：
                1. word 使用规范拼写。
                2. 至少返回一个词性对象和一个 definitions 对象。
                3. translation 放在 definitions.translation 中，不要额外扩展字段。
                4. exampleSentences 尽量给一个常见、自然的例句。
                5. difficulty 取 1-5 的整数，默认按常见学习难度估计。
                6. syllableDetail.segments 必须按顺序拼接后严格还原 word；无法确认时返回空数组。
                """.formatted(word, word);

        return List.of(
                new AiChatMessageRequest("system", systemPrompt),
                new AiChatMessageRequest("user", userPrompt)
        );
    }
}
