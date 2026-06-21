import { useEffect, useState } from 'react';
import { BookOpen, CaretRight } from '@phosphor-icons/react';
import { dictionaryWordApi } from '../api';
import type { Dictionary, MetaWord } from '../types';
import { SyllableReader } from './SyllableReader';

export function StudentLibrary({ dictionaries }: { dictionaries: Dictionary[] }) {
  const [selectedDictionaryId, setSelectedDictionaryId] = useState<number | null>(null);
  const [words, setWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
        setSelectedWord(page.content[0] ?? null);
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : '词书加载失败');
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => { active = false; };
  }, [selectedDictionaryId]);

  return (
    <>
      <div className="section-header"><div><p className="eyebrow">Word Library</p><h2>词库与复习</h2></div></div>
      <div className="segmented"><button type="button" className="is-active">我的词书</button><button type="button" disabled>错词本</button><button type="button" disabled>收藏</button></div>

      {dictionaries.length === 0 ? (
        <section className="library-empty"><BookOpen size={32} /><h2>还没有可用词书</h2><p>教师分配词书后，会自动出现在这里。</p></section>
      ) : (
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
                    {!loading && !error && (
                      <>
                        <div className="word-chip-list">
                          {words.map((word) => <button type="button" key={word.id} className={selectedWord?.id === word.id ? 'is-active' : ''} onClick={() => setSelectedWord(word)}>{word.word}</button>)}
                        </div>
                        {selectedWord && (
                          <div className="library-word-detail">
                            <h3>{selectedWord.word}</h3>
                            {selectedWord.phonetic && <p className="phonetic">{selectedWord.phonetic}</p>}
                            <p>{selectedWord.definition || selectedWord.translation || '暂无释义'}</p>
                            <SyllableReader word={selectedWord.word} detail={selectedWord.syllableDetail} />
                            {selectedWord.exampleSentence && <div className="example-strip"><span>例句</span><p>{selectedWord.exampleSentence}</p></div>}
                          </div>
                        )}
                      </>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </section>
      )}
    </>
  );
}
