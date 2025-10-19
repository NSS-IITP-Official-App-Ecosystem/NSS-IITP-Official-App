import { Card } from "./ui/card";
import { ScrollArea } from "./ui/scroll-area";
import { FileQuestion } from "lucide-react";

interface AnswerPanelProps {
  question: string | null;
  answer: string | null;
}

export function AnswerPanel({ question, answer }: AnswerPanelProps) {
  if (!question || !answer) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-muted-foreground p-8">
        <FileQuestion className="h-16 w-16 mb-4 opacity-20" />
        <p className="text-center">Select a question to view the answer</p>
      </div>
    );
  }

  return (
    <ScrollArea className="h-full">
      <div className="p-6 space-y-4">
        <div>
          <h2 className="mb-3 text-primary">Question</h2>
          <Card className="p-4 bg-muted/50">
            <p>{question}</p>
          </Card>
        </div>
        <div>
          <h2 className="mb-3 text-primary">Answer</h2>
          <Card className="p-4">
            <div className="prose prose-sm max-w-none">
              <p>{answer}</p>
            </div>
          </Card>
        </div>
      </div>
    </ScrollArea>
  );
}
