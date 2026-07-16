import { Fragment, useEffect, useState } from 'react';
import { BookOpen, CaretRight, Star } from '@phosphor-icons/react';
import { dictionaryWordApi, studentWordMemoryApi } from '../api';
import type { Dictionary, MetaWord, StudentWordMemory } from '../types';
import { SyllableReader } from './SyllableReader';

type LibraryTab = 'dictionaries' | 'wrong' | 'favorites';

export function StudentLibrary({
  dictionaries,
  initialDictionaryId,
}: {
  dictionaries: Dictionary[];
  initialDictionaryId?: number | null;
}) {
  const [tab, setTab] = useState<LibraryTab>('dictionaries');
  const [selectedDictionaryId, setSelectedDictionaryId] = useState<number | null>(null);
  const [words, setWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [selectedMemory, setSelectedMemory] = useState<StudentWordMemory | null>(null);
  const [memoryByWordId, setMemoryByWordId] = useState<Record<number, StudentWordMemory>>({});
  const [wrongWords, setWrongWords] = useState<StudentWordMemory[]>([]);
  const [favoriteWords, setFavoriteWords] = useState<StudentWordMemory[]>([]);
  const [loading, setLoading] = useState(false);
  const [memoryLoading, setMemoryLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memoryError, setMemoryError] = useState<string | null>(null);
  const [favoriteUpdatingId, setFavoriteUpdatingId] = useState<number | null>(null);

  useEffect(() => {
    if (initialDictionaryId) {
      setTab('dictionaries');
      setSelectedDictionaryId(initialDictionaryId);
    }
  }, [initialDictionaryId]);

  const selectTab = (nextTab: LibraryTab) => {
    setTab(nextTab);
    setError(null);
    setMemoryError(null);
    setSelectedWord(null);
    setSelectedMemory(null);
  };

  useEffect(() => {
    if (selectedDictionaryId === null) return;
    let active = true;
    void Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true);
      setError(null);
      try {
        const page = await dictionaryWordApi.getWordsByDictionary(selectedDictionaryId, 1, 100);
        if (!active) return;
        setWords(page.content);
        setSelectedWord(null);
        setSelectedMemory(null);
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : '词书加载失败');
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => { active = false; };
  }, [selectedDictionaryId]);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(async () => {
      try {
        const memories = await studentWordMemoryApi.list();
        if (!active) return;
        setMemoryByWordId(toMemoryMap(memories));
      } catch {
        if (active) setMemoryByWordId({});
      }
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (tab === 'dictionaries') return;
    let active = true;
    void Promise.resolve().then(async () => {
      setMemoryLoading(true);
      setMemoryError(null);
      try {
        const memories = tab === 'wrong'
          ? await studentWordMemoryApi.listWrongWords()
          : await studentWordMemoryApi.listFavoriteWords();
        if (!active) return;
        if (tab === 'wrong') {
          setWrongWords(memories);
        } else {
          setFavoriteWords(memories);
        }
        setMemoryByWordId((current) => ({ ...current, ...toMemoryMap(memories) }));
        setSelectedMemory(memories[0] ?? null);
      } catch (loadError) {
        if (active) setMemoryError(loadError instanceof Error ? loadError.message : '记忆列表加载失败');
      } finally {
        if (active) setMemoryLoading(false);
      }
    });
    return () => { active = false; };
  }, [tab]);

  const updateFavoriteState = (updated: StudentWordMemory) => {
    setMemoryByWordId((current) => ({ ...current, [updated.metaWordId]: updated }));
    setWrongWords((current) => current.map((memory) => memory.metaWordId === updated.metaWordId ? updated : memory));
    setFavoriteWords((current) => {
      const withoutUpdated = current.filter((memory) => memory.metaWordId !== updated.metaWordId);
      return updated.favorite ? [updated, ...withoutUpdated] : withoutUpdated;
    });
    setSelectedMemory((current) => current?.metaWordId === updated.metaWordId ? updated : current);
  };

  const toggleFavorite = async (metaWordId: number, favorite: boolean) => {
    setFavoriteUpdatingId(metaWordId);
    setMemoryError(null);
    try {
      updateFavoriteState(await studentWordMemoryApi.updateFavorite(metaWordId, favorite));
    } catch (updateError) {
      setMemoryError(updateError instanceof Error ? updateError.message : '收藏状态更新失败');
    } finally {
      setFavoriteUpdatingId(null);
    }
  };

  return (
    <>
      <div className="section-header"><div><p className="eyebrow">Word Library</p><h2>词库与复习</h2></div></div>
      <div className="segmented">
        <button type="button" className={tab === 'dictionaries' ? 'is-active' : ''} onClick={() => selectTab('dictionaries')}>我的词书</button>
        <button type="button" className={tab === 'wrong' ? 'is-active' : ''} onClick={() => selectTab('wrong')}>错词本</button>
        <button type="button" className={tab === 'favorites' ? 'is-active' : ''} onClick={() => selectTab('favorites')}>收藏</button>
      </div>
      {memoryError && <p className="inline-error" role="alert">{memoryError}</p>}

      {tab === 'dictionaries' && dictionaries.length === 0 ? (
        <section className="library-empty"><BookOpen size={32} /><h2>还没有可用词书</h2><p>教师分配词书后，会自动出现在这里。</p></section>
      ) : tab === 'dictionaries' ? (
        <section className="dictionary-list">
          {dictionaries.map((dictionary) => {
            const expanded = selectedDictionaryId === dictionary.id;
            return (
              <article key={dictionary.id} className={`dictionary-card ${expanded ? 'is-expanded' : ''}`}>
                <button type="button" className="dictionary-card__main" onClick={() => setSelectedDictionaryId(expanded ? null : dictionary.id)} aria-expanded={expanded}>
                  <div><p className="eyebrow">Assigned Dictionary</p><h2>{dictionary.name}</h2><span>{dictionary.wordCount ?? 0} 个词 · 已分配词书</span></div>
                  <div className="dictionary-card__progress"><strong>{dictionary.wordCount ?? 0}</strong><span>词条</span><CaretRight size={20} /></div>
                </button>
                {expanded && (
                  <div className="dictionary-card__words">
                    {loading && <p className="inline-note">正在加载词条...</p>}
                    {error && <p className="inline-error" role="alert">{error}</p>}
                    {!loading && !error && words.length === 0 && (
                      <p className="inline-note">这本词书还没有词条。</p>
                    )}
                    {!loading && !error && words.length > 0 && (
                      <>
                        {renderDictionaryWordBrowser(
                          words,
                          selectedWord,
                          memoryByWordId,
                          favoriteUpdatingId,
                          (word) => {
                            setSelectedWord(word);
                            setSelectedMemory(null);
                          },
                          toggleFavorite,
                        )}
                      </>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </section>
      ) : (
        <MemoryList
          title={tab === 'wrong' ? '自动错词本' : '手动收藏夹'}
          empty={tab === 'wrong' ? '还没有自动错词' : '还没有收藏词'}
          loading={memoryLoading}
          memories={tab === 'wrong' ? wrongWords : favoriteWords}
          selectedMemory={selectedMemory}
          onSelect={(memory) => {
            setSelectedMemory(memory);
            setSelectedWord(null);
          }}
          favoriteUpdatingId={favoriteUpdatingId}
          onToggleFavorite={toggleFavorite}
        />
      )}
    </>
  );
}

function MemoryList({
  title,
  empty,
  loading,
  memories,
  selectedMemory,
  favoriteUpdatingId,
  onSelect,
  onToggleFavorite,
}: {
  title: string;
  empty: string;
  loading: boolean;
  memories: StudentWordMemory[];
  selectedMemory: StudentWordMemory | null;
  favoriteUpdatingId: number | null;
  onSelect: (memory: StudentWordMemory) => void;
  onToggleFavorite: (metaWordId: number, favorite: boolean) => Promise<void>;
}) {
  if (loading) {
    return <section className="library-empty"><BookOpen size={32} /><h2>正在加载</h2><p>正在整理你的复习词。</p></section>;
  }

  if (memories.length === 0) {
    return <section className="library-empty"><BookOpen size={32} /><h2>{empty}</h2><p>学习中答错、跳过或主动收藏后会出现在这里。</p></section>;
  }

  const detail = selectedMemory ?? memories[0];

  return (
    <section className="memory-list">
      <div className="section-header"><div><p className="eyebrow">Review</p><h2>{title}</h2></div><span className="subtle-count">{memories.length} 词</span></div>
      <div className="word-chip-list memory-chip-list">
        {memories.map((memory) => (
          <button
            type="button"
            key={memory.metaWordId}
            className={detail.metaWordId === memory.metaWordId ? 'is-active' : ''}
            onClick={() => onSelect(memory)}
          >
            {memory.word}
          </button>
        ))}
      </div>
      {renderWordDetail(
        memoryToWord(detail),
        detail,
        detail.favorite,
        favoriteUpdatingId === detail.metaWordId,
        onToggleFavorite,
      )}
    </section>
  );
}

function renderDictionaryWordBrowser(
  words: MetaWord[],
  selectedWord: MetaWord | null,
  memoryByWordId: Record<number, StudentWordMemory>,
  favoriteUpdatingId: number | null,
  onSelect: (word: MetaWord) => void,
  onToggleFavorite: (metaWordId: number, favorite: boolean) => Promise<void>,
) {
  const selectedIndex = selectedWord
    ? words.findIndex((word) => word.id === selectedWord.id)
    : -1;

  return (
    <div className="dictionary-word-browser">
      <div className="dictionary-word-browser__top">
        <div>
          <p className="eyebrow">Words</p>
          <strong>{words.length} 个词条</strong>
        </div>
        <span>{selectedIndex >= 0 ? `#${selectedIndex + 1}` : '未选择'}</span>
      </div>
      <div className="library-word-grid">
        {words.map((word, index) => {
          const memory = memoryByWordId[word.id];
          const active = selectedWord?.id === word.id;
          const favorite = memory?.favorite ?? false;
          const preview = getWordPreview(word);
          const phonetic = getPhoneticDisplay(word);
          const partOfSpeech = word.partOfSpeech?.trim();
          const cardClass = [
            'library-word-card',
            active ? 'is-active' : '',
            memory?.favorite ? 'is-favorite' : '',
          ].filter(Boolean).join(' ');

          return (
            <Fragment key={word.id}>
              <button
                type="button"
                className={cardClass}
                onClick={() => onSelect(word)}
                aria-pressed={active}
              >
                <span className="library-word-card__index">{String(index + 1).padStart(2, '0')}</span>
                <span className="library-word-card__body">
                  <span className="library-word-card__title">
                    <strong>{word.word}</strong>
                    {memory?.favorite && <Star size={15} weight="fill" />}
                  </span>
                  <span className="library-word-card__meta">
                    {partOfSpeech && <span>{partOfSpeech}</span>}
                    {phonetic && <span>{phonetic}</span>}
                    {memory && <span>掌握 {Math.round(memory.masteryLevel)}%</span>}
                  </span>
                  <span className="library-word-card__preview">{preview}</span>
                </span>
              </button>
              {active && renderWordDetail(
                word,
                memory,
                favorite,
                favoriteUpdatingId === word.id,
                onToggleFavorite,
                true,
              )}
            </Fragment>
          );
        })}
      </div>
    </div>
  );
}

function renderWordDetail(
  word: MetaWord,
  memory: StudentWordMemory | undefined,
  favorite: boolean,
  favoriteUpdating: boolean,
  onToggleFavorite: (metaWordId: number, favorite: boolean) => Promise<void>,
  inline = false,
) {
  const phonetic = getPhoneticDisplay(word);
  const partOfSpeech = word.partOfSpeech?.trim();
  const definition = word.definition || word.translation || '暂无释义';
  const hasSeparateTranslation = Boolean(word.definition && word.translation && word.translation !== word.definition);

  return (
    <div className={`library-word-detail ${inline ? 'library-word-detail--inline' : ''}`}>
      <div className="library-word-detail__heading">
        <div>
          <p className="eyebrow">Selected Word</p>
          <h3>{word.word}</h3>
        </div>
        <button
          type="button"
          className={favorite ? 'favorite-toggle is-active' : 'favorite-toggle'}
          onClick={() => void onToggleFavorite(word.id, !favorite)}
          disabled={favoriteUpdating}
          aria-pressed={favorite}
          aria-label={favorite ? '取消收藏' : '收藏单词'}
        >
          <Star size={20} weight={favorite ? 'fill' : 'regular'} />
        </button>
      </div>
      {phonetic && <p className="phonetic">{phonetic}</p>}
      {(partOfSpeech || typeof word.difficulty === 'number') && (
        <div className="library-word-detail__badges">
          {partOfSpeech && <span>{partOfSpeech}</span>}
          {typeof word.difficulty === 'number' && <span>难度 {word.difficulty}</span>}
        </div>
      )}
      {memory && (
        <div className="memory-stats" aria-label="记忆状态">
          <span>盒位 {memory.boxLevel}</span>
          <span>掌握 {Math.round(memory.masteryLevel)}%</span>
          <span>错 {memory.wrongTimes}</span>
        </div>
      )}
      <div className="library-word-detail__definition">
        <span>释义</span>
        <p>{definition}</p>
      </div>
      {hasSeparateTranslation && (
        <div className="library-word-detail__definition library-word-detail__definition--quiet">
          <span>译文</span>
          <p>{word.translation}</p>
        </div>
      )}
      <SyllableReader word={word.word} detail={word.syllableDetail} />
      {word.exampleSentence && <div className="example-strip"><span>例句</span><p>{word.exampleSentence}</p></div>}
    </div>
  );
}

function toMemoryMap(memories: StudentWordMemory[]) {
  return memories.reduce<Record<number, StudentWordMemory>>((accumulator, memory) => {
    accumulator[memory.metaWordId] = memory;
    return accumulator;
  }, {});
}

function memoryToWord(memory: StudentWordMemory): MetaWord {
  return {
    id: memory.metaWordId,
    word: memory.word,
    phonetic: memory.phonetic ?? undefined,
    definition: memory.definition ?? undefined,
    translation: memory.translation ?? undefined,
    partOfSpeech: memory.partOfSpeech ?? undefined,
    exampleSentence: memory.exampleSentence ?? undefined,
    phoneticDetail: memory.phoneticDetail,
    syllableDetail: memory.syllableDetail,
  };
}

function getPhoneticDisplay(word: MetaWord) {
  return word.phoneticDetail?.us || word.phonetic || word.phoneticDetail?.uk || '';
}

function getWordPreview(word: MetaWord) {
  const text = word.translation || word.definition || word.exampleSentence || '暂无释义';
  return text.length > 54 ? `${text.slice(0, 54)}...` : text;
}
