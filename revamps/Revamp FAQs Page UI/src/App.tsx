import { useState } from "react";
import { TopBar } from "./components/TopBar";
import { SearchBar } from "./components/SearchBar";
import { AnswerPanel } from "./components/AnswerPanel";
import { QuestionTree, type Section, type Question } from "./components/QuestionTree";
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "./components/ui/resizable";
import { toast } from "sonner@2.0.3";

// Mock FAQ data
const mockFAQData: Section[] = [
  {
    id: "section-1",
    title: "Getting Started",
    subSections: [
      {
        id: "subsection-1-1",
        title: "Account Setup",
        questions: [
          {
            id: "q-1-1-1",
            title: "How do I create an account?",
            answer: "To create an account, tap on the 'Sign Up' button on the home screen. Enter your email address, create a password, and verify your email through the link sent to your inbox. Once verified, you can log in and start using the app.",
          },
          {
            id: "q-1-1-2",
            title: "Can I use my social media account to sign up?",
            answer: "Yes, you can sign up using your Google, Facebook, or Apple account. Simply tap on the respective social media icon during the sign-up process and authorize the connection.",
          },
          {
            id: "q-1-1-3",
            title: "How do I reset my password?",
            answer: "If you've forgotten your password, tap on 'Forgot Password' on the login screen. Enter your email address and we'll send you a link to reset your password. Follow the instructions in the email to create a new password.",
          },
        ],
      },
      {
        id: "subsection-1-2",
        title: "Basic Features",
        questions: [
          {
            id: "q-1-2-1",
            title: "How do I navigate the main interface?",
            answer: "The main interface consists of a bottom navigation bar with five tabs: Home, Search, Messages, Notifications, and Profile. Tap on any tab to navigate to that section. Swipe left or right to move between adjacent sections.",
          },
          {
            id: "q-1-2-2",
            title: "What are the quick actions available?",
            answer: "Quick actions can be accessed by long-pressing on items throughout the app. Common quick actions include share, bookmark, copy link, and report. The available actions depend on the type of content you're interacting with.",
          },
        ],
      },
    ],
  },
  {
    id: "section-2",
    title: "Account Management",
    subSections: [
      {
        id: "subsection-2-1",
        title: "Profile Settings",
        questions: [
          {
            id: "q-2-1-1",
            title: "How do I update my profile information?",
            answer: "Navigate to your Profile tab, tap on 'Edit Profile', and you can update your name, bio, profile picture, and other personal information. Don't forget to tap 'Save' when you're done making changes.",
          },
          {
            id: "q-2-1-2",
            title: "Can I make my profile private?",
            answer: "Yes, go to Settings > Privacy and toggle 'Private Account'. When your account is private, only approved followers can see your posts and profile information.",
          },
        ],
      },
      {
        id: "subsection-2-2",
        title: "Security",
        questions: [
          {
            id: "q-2-2-1",
            title: "How do I enable two-factor authentication?",
            answer: "Go to Settings > Security > Two-Factor Authentication. You can choose to receive codes via SMS or use an authenticator app. Follow the on-screen instructions to complete the setup process.",
          },
          {
            id: "q-2-2-2",
            title: "How do I review my login history?",
            answer: "Navigate to Settings > Security > Login Activity to see a list of all devices and locations where your account has been accessed. You can log out of any suspicious sessions from this screen.",
          },
        ],
      },
    ],
  },
  {
    id: "section-3",
    title: "Troubleshooting",
    subSections: [
      {
        id: "subsection-3-1",
        title: "Common Issues",
        questions: [
          {
            id: "q-3-1-1",
            title: "The app keeps crashing, what should I do?",
            answer: "First, try force-closing the app and reopening it. If the issue persists, check if you have the latest version of the app installed. You can also try clearing the app cache in your device settings or reinstalling the app. If none of these work, contact our support team.",
          },
          {
            id: "q-3-1-2",
            title: "My notifications aren't working",
            answer: "Check your device settings to ensure notifications are enabled for our app. Also verify your in-app notification preferences in Settings > Notifications. Make sure you have a stable internet connection as notifications require connectivity.",
          },
          {
            id: "q-3-1-3",
            title: "Why is the app running slowly?",
            answer: "App performance can be affected by low storage space on your device, poor internet connection, or running too many apps simultaneously. Try closing other apps, freeing up storage space, and ensuring you have a stable connection. Updating to the latest app version can also help improve performance.",
          },
        ],
      },
      {
        id: "subsection-3-2",
        title: "Data & Storage",
        questions: [
          {
            id: "q-3-2-1",
            title: "How much storage does the app use?",
            answer: "The base app size is approximately 50MB, but this can grow depending on cached data and downloaded content. You can check the exact storage usage in your device settings under Apps > [App Name] > Storage.",
          },
          {
            id: "q-3-2-2",
            title: "How do I clear the app cache?",
            answer: "You can clear the cache in two ways: 1) Through the app by going to Settings > Storage > Clear Cache, or 2) Through your device settings by navigating to Apps > [App Name] > Storage > Clear Cache. This won't delete your account data.",
          },
        ],
      },
    ],
  },
  {
    id: "section-4",
    title: "Privacy & Safety",
    subSections: [
      {
        id: "subsection-4-1",
        title: "Data Protection",
        questions: [
          {
            id: "q-4-1-1",
            title: "How is my data protected?",
            answer: "We use industry-standard encryption to protect your data both in transit and at rest. All communication between the app and our servers is encrypted using TLS 1.3. We also implement strict access controls and regularly audit our security measures.",
          },
          {
            id: "q-4-1-2",
            title: "Can I download my data?",
            answer: "Yes, you have the right to download a copy of your data. Go to Settings > Privacy > Download Your Data. We'll prepare a file containing your information and notify you when it's ready for download, typically within 48 hours.",
          },
        ],
      },
      {
        id: "subsection-4-2",
        title: "Reporting & Blocking",
        questions: [
          {
            id: "q-4-2-1",
            title: "How do I report inappropriate content?",
            answer: "Tap on the three-dot menu on any post or profile, then select 'Report'. Choose the reason for your report and provide any additional details. Our moderation team reviews all reports within 24 hours.",
          },
          {
            id: "q-4-2-2",
            title: "How do I block a user?",
            answer: "Go to the user's profile, tap the three-dot menu, and select 'Block User'. Blocked users won't be able to see your profile, send you messages, or interact with your content. You can manage your blocked users list in Settings > Privacy > Blocked Users.",
          },
        ],
      },
    ],
  },
];

export default function App() {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedQuestion, setSelectedQuestion] = useState<Question | null>(null);

  const handleBack = () => {
    toast("Back button clicked");
  };

  const handleRefresh = () => {
    setSearchQuery("");
    setSelectedQuestion(null);
    toast("FAQs refreshed");
  };

  const handleEdit = () => {
    toast("Edit mode activated");
  };

  const handleQuestionSelect = (question: Question) => {
    setSelectedQuestion(question);
  };

  return (
    <div className="h-screen flex flex-col bg-background">
      <TopBar onBack={handleBack} onRefresh={handleRefresh} onEdit={handleEdit} />
      <SearchBar value={searchQuery} onChange={setSearchQuery} />
      
      <ResizablePanelGroup direction="vertical" className="flex-1">
        <ResizablePanel defaultSize={40} minSize={20}>
          <AnswerPanel 
            question={selectedQuestion?.title || null} 
            answer={selectedQuestion?.answer || null} 
          />
        </ResizablePanel>
        
        <ResizableHandle className="bg-border hover:bg-primary/20 transition-colors" />
        
        <ResizablePanel defaultSize={60} minSize={30}>
          <QuestionTree
            sections={mockFAQData}
            onQuestionSelect={handleQuestionSelect}
            selectedQuestionId={selectedQuestion?.id || null}
            searchQuery={searchQuery}
          />
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  );
}
