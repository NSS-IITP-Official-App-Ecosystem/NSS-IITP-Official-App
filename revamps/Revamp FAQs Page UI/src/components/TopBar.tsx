import { ArrowLeft, RefreshCw, Edit } from "lucide-react";
import { Button } from "./ui/button";

interface TopBarProps {
  onBack: () => void;
  onRefresh: () => void;
  onEdit: () => void;
}

export function TopBar({ onBack, onRefresh, onEdit }: TopBarProps) {
  return (
    <div className="flex items-center justify-between px-4 py-3 border-b bg-background">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={onBack}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <h1 className="text-lg">FAQs</h1>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" onClick={onRefresh}>
          <RefreshCw className="h-5 w-5" />
        </Button>
        <Button variant="ghost" size="icon" onClick={onEdit}>
          <Edit className="h-5 w-5" />
        </Button>
      </div>
    </div>
  );
}
