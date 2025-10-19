import { useState } from "react";
import { ChevronRight, Folder, FolderOpen, FileQuestion } from "lucide-react";
import { ScrollArea } from "./ui/scroll-area";
import { cn } from "./ui/utils";

export interface Question {
  id: string;
  title: string;
  answer: string;
}

export interface SubSection {
  id: string;
  title: string;
  questions: Question[];
}

export interface Section {
  id: string;
  title: string;
  subSections: SubSection[];
}

interface QuestionTreeProps {
  sections: Section[];
  onQuestionSelect: (question: Question) => void;
  selectedQuestionId: string | null;
  searchQuery: string;
}

export function QuestionTree({ sections, onQuestionSelect, selectedQuestionId, searchQuery }: QuestionTreeProps) {
  const [openSections, setOpenSections] = useState<Set<string>>(new Set());
  const [openSubSections, setOpenSubSections] = useState<Set<string>>(new Set());

  const toggleSection = (sectionId: string) => {
    const newOpenSections = new Set(openSections);
    if (newOpenSections.has(sectionId)) {
      newOpenSections.delete(sectionId);
    } else {
      newOpenSections.add(sectionId);
    }
    setOpenSections(newOpenSections);
  };

  const toggleSubSection = (subSectionId: string) => {
    const newOpenSubSections = new Set(openSubSections);
    if (newOpenSubSections.has(subSectionId)) {
      newOpenSubSections.delete(subSectionId);
    } else {
      newOpenSubSections.add(subSectionId);
    }
    setOpenSubSections(newOpenSubSections);
  };

  const filterSections = () => {
    if (!searchQuery.trim()) return sections;

    const lowerQuery = searchQuery.toLowerCase();
    return sections
      .map((section) => ({
        ...section,
        subSections: section.subSections
          .map((subSection) => ({
            ...subSection,
            questions: subSection.questions.filter(
              (q) =>
                q.title.toLowerCase().includes(lowerQuery) ||
                q.answer.toLowerCase().includes(lowerQuery)
            ),
          }))
          .filter((subSection) => subSection.questions.length > 0),
      }))
      .filter((section) => section.subSections.length > 0);
  };

  const filteredSections = filterSections();

  return (
    <ScrollArea className="h-full">
      <div className="p-4 space-y-1">
        {filteredSections.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">
            <p>No FAQs found</p>
          </div>
        ) : (
          filteredSections.map((section) => (
            <div key={section.id}>
              {/* Section */}
              <button
                onClick={() => toggleSection(section.id)}
                className="flex items-center gap-2 w-full px-3 py-2 rounded-md hover:bg-accent transition-colors text-left"
              >
                <ChevronRight
                  className={cn(
                    "h-4 w-4 transition-transform flex-shrink-0",
                    openSections.has(section.id) && "rotate-90"
                  )}
                />
                {openSections.has(section.id) ? (
                  <FolderOpen className="h-4 w-4 text-primary flex-shrink-0" />
                ) : (
                  <Folder className="h-4 w-4 text-primary flex-shrink-0" />
                )}
                <span className="truncate">{section.title}</span>
              </button>

              {/* SubSections */}
              {openSections.has(section.id) && (
                <div className="ml-4 space-y-1 mt-1">
                  {section.subSections.map((subSection) => (
                    <div key={subSection.id}>
                      <button
                        onClick={() => toggleSubSection(subSection.id)}
                        className="flex items-center gap-2 w-full px-3 py-2 rounded-md hover:bg-accent transition-colors text-left"
                      >
                        <ChevronRight
                          className={cn(
                            "h-4 w-4 transition-transform flex-shrink-0",
                            openSubSections.has(subSection.id) && "rotate-90"
                          )}
                        />
                        {openSubSections.has(subSection.id) ? (
                          <FolderOpen className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                        ) : (
                          <Folder className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                        )}
                        <span className="truncate">{subSection.title}</span>
                      </button>

                      {/* Questions */}
                      {openSubSections.has(subSection.id) && (
                        <div className="ml-4 space-y-1 mt-1">
                          {subSection.questions.map((question) => (
                            <button
                              key={question.id}
                              onClick={() => onQuestionSelect(question)}
                              className={cn(
                                "flex items-center gap-2 w-full px-3 py-2 rounded-md transition-colors text-left",
                                selectedQuestionId === question.id
                                  ? "bg-primary text-primary-foreground"
                                  : "hover:bg-accent"
                              )}
                            >
                              <FileQuestion className="h-4 w-4 flex-shrink-0" />
                              <span className="truncate">{question.title}</span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </ScrollArea>
  );
}
