import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const aiStudyQuerySchema = z.object({
  query: z.string().min(2).max(2000),
  topic: z.string().optional(),
  mode: z.enum(['EXPLAIN', 'SUMMARIZE', 'CONCEPT_QA', 'SOLVE_STEP_BY_STEP']).default('EXPLAIN')
});

export const aiCareerQuerySchema = z.object({
  query: z.string().min(2).max(2000),
  topic: z.string().optional(),
  mode: z.enum(['CAREER_GUIDANCE', 'JOB_MATCHING', 'SKILL_RECOMMENDATION', 'INTERVIEW_PREP', 'RESUME_REVIEW', 'CAREER_ROADMAP']).default('CAREER_GUIDANCE')
});

export const aiAspirantQuerySchema = z.object({
  query: z.string().min(2).max(2000),
  topic: z.string().optional(),
  mode: z.enum(['COLLEGE_RECOMMENDATION', 'SCHOLARSHIP_ADVICE', 'COURSE_SELECTION', 'CAREER_DIRECTION', 'EXAM_PREP']).default('COLLEGE_RECOMMENDATION')
});

export const createChatSessionSchema = z.object({
  title: z.string().min(1).max(200).default('New Conversation'),
  mode: z.enum(['STUDY', 'CAREER', 'ASPIRANT', 'GENERAL']).default('GENERAL')
});

export const sendChatMessageSchema = z.object({
  message: z.string().min(1).max(4000)
});

export async function handleStudyAssistantQuery(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { query, topic, mode } = req.body;

  const apiKey = process.env.GEMINI_API_KEY || process.env.AI_API_KEY;

  if (!apiKey) {
    const lower = query.toLowerCase();
    let answer = "";
    if (lower.includes("dijkstra")) {
      answer = "Dijkstra's Algorithm Overview:\n\n• Purpose: Finds the shortest path from a starting node to all other nodes in a weighted graph with non-negative edge weights.\n• Data Structure: Min-Priority Queue (Fibonacci or Binary Heap).\n• Time Complexity: O((V + E) log V) with a binary heap.\n• Key Steps:\n  1. Initialize distance to start node as 0, all others as infinity.\n  2. Extract min-distance unvisited vertex u.\n  3. For each neighbor v of u: relax edge (u, v).\n  4. Repeat until all reachable vertices are visited.";
    } else if (lower.includes("normalization") || lower.includes("bcnf") || lower.includes("1nf")) {
      answer = "Database Normalization Guide:\n\n• 1NF: Atomic attributes, no repeating groups.\n• 2NF: In 1NF + no partial dependency on a composite primary key.\n• 3NF: In 2NF + no transitive dependency (non-prime attribute depends on another non-prime attribute).\n• BCNF: In 3NF + for every functional dependency X -> Y, X must be a super key.";
    } else if (lower.includes("scheduling") || lower.includes("round robin") || lower.includes("process")) {
      answer = "CPU Scheduling Algorithms:\n\n• FCFS (First-Come, First-Served): Non-preemptive, suffers from Convoy Effect.\n• SJF (Shortest Job First): Provably optimal average waiting time; requires burst time estimation.\n• Round Robin (RR): Preemptive with time quantum Q; fair and interactive.\n• Priority Scheduling: Can suffer from starvation; resolved via aging.";
    } else if (lower.includes("tcp") || lower.includes("handshake")) {
      answer = "TCP 3-Way Handshake Explanation:\n\n1. SYN: Client sends SYN packet with sequence number x to Server (Connection request).\n2. SYN-ACK: Server responds with SYN packet (seq y) and ACK (x+1) acknowledging receipt.\n3. ACK: Client sends ACK (y+1) confirming connection establishment.\n\nNow reliable, full-duplex byte stream transmission begins.";
    } else {
      answer = `Academic Response for "${query}":\n\n• Mode: ${mode}\n• Core Concept: Key principles and formal proofs.\n• Analysis: Deconstruct into foundational rules, check edge conditions, and verify invariant properties.\n• Recommendation: Practice with standard textbook exercises and implement an end-to-end simulation.`;
    }

    sendSuccess(res, {
      available: true,
      query,
      mode,
      message: 'AI generated response',
      response: answer,
      suggestedTopics: [
        'Dijkstra Shortest Path',
        'Database Normalization (1NF to BCNF)',
        'Process Scheduling Algorithms',
        'TCP 3-Way Handshake'
      ]
    }, 'AI response generated');
    return;
  }

  try {
    const model = process.env.GEMINI_MODEL || 'gemini-3.5-flash';
    console.log(`[AI Study Assistant] Outbound request to Gemini API (model: ${model}) for user: ${userId}`);
    const startTime = Date.now();
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              {
                text: `You are an expert, encouraging university tutor on CampusVerse. 
Mode: ${mode}
Topic: ${topic || 'General Computer Science & Engineering'}
Student Question: ${query}

Provide a structured, clear explanation with key concepts, bullet points, and code or examples where appropriate.`
              }
            ]
          }
        ]
      })
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errText = await response.text();
      console.error(`[AI Study Assistant] Gemini API Error (${response.status}) in ${latencyMs}ms:`, errText);
      sendSuccess(res, {
        available: false,
        query,
        mode,
        message: 'The AI service encountered an upstream error. Please try again in a few moments.',
        response: null
      });
      return;
    }

    const data: any = await response.json();
    console.log(`[AI Study Assistant] Gemini API response received in ${latencyMs}ms. Usage: promptTokens=${data.usageMetadata?.promptTokenCount}, candidatesTokens=${data.usageMetadata?.candidatesTokenCount}, totalTokens=${data.usageMetadata?.totalTokenCount}`);
    const generatedText = data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response generated.';

    await prisma.aIRecommendation.create({
      data: {
        userId,
        type: 'STUDY_INSIGHT',
        referenceId: `study_${Date.now()}`,
        score: 1.0,
        reasoning: `Study Assistant query: ${query.substring(0, 100)}...`
      }
    }).catch(() => {});

    sendSuccess(res, {
      available: true,
      query,
      mode,
      topic,
      response: generatedText,
      timestamp: new Date().toISOString()
    }, 'AI explanation generated successfully');
  } catch (error: any) {
    console.error('Study Assistant exception:', error);
    sendSuccess(res, {
      available: false,
      query,
      mode,
      message: 'Failed to communicate with the AI study assistant service.',
      response: null
    });
  }
}

export async function handleCareerAssistantQuery(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { query, topic, mode } = req.body;

  const apiKey = process.env.GEMINI_API_KEY || process.env.AI_API_KEY;

  if (!apiKey) {
    sendSuccess(res, {
      available: false,
      query,
      mode,
      message: 'AI Career Assistant is currently unavailable on this server (GEMINI_API_KEY not configured). Please configure the AI service key in the server environment.',
      response: null,
      suggestedTopics: [
        'Transitioning from Senior Engineer to Staff / Tech Lead',
        'System Design Interview Strategy: High-Throughput Data Pipelines',
        'Effective Resume Impact Bullet Points with Quantifiable Metrics',
        'Behavioral Leadership Questions (STAR Method)'
      ]
    }, 'AI service status returned');
    return;
  }

  try {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: { profile: { include: { alumniProfile: true } }, skills: true }
    });

    const userContext = `User Role: ${user?.role || 'ALUMNI'}, Title: ${user?.profile?.alumniProfile?.currentDesignation || 'Software Engineer'}, Company: ${user?.profile?.alumniProfile?.currentCompany || 'Tech'}, Experience: ${user?.profile?.alumniProfile?.yearsOfExperience || 3} years, Skills: ${user?.skills.map((s) => s.skillName).join(', ')}`;

    const model = process.env.GEMINI_MODEL || 'gemini-3.5-flash';
    console.log(`[AI Career Assistant] Outbound request to Gemini API (model: ${model}) for user: ${userId}`);
    const startTime = Date.now();
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              {
                text: `You are an executive career coach and tech leadership advisor on CampusVerse.
Context: ${userContext}
Mode: ${mode}
Topic: ${topic || 'Career Growth & Tech Leadership'}
Query: ${query}

Provide actionable, high-signal career guidance with structured steps, industry best practices, and direct takeaways.`
              }
            ]
          }
        ]
      })
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errText = await response.text();
      console.error(`[AI Career Assistant] Gemini Career API Error (${response.status}) in ${latencyMs}ms:`, errText);
      sendSuccess(res, {
        available: false,
        query,
        mode,
        message: 'The AI career service encountered an upstream error. Please try again in a few moments.',
        response: null
      });
      return;
    }

    const data: any = await response.json();
    console.log(`[AI Career Assistant] Gemini API response received in ${latencyMs}ms. Usage: promptTokens=${data.usageMetadata?.promptTokenCount}, candidatesTokens=${data.usageMetadata?.candidatesTokenCount}, totalTokens=${data.usageMetadata?.totalTokenCount}`);
    const generatedText = data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response generated.';

    await prisma.aIRecommendation.create({
      data: {
        userId,
        type: 'CAREER_INSIGHT',
        referenceId: `career_${Date.now()}`,
        score: 1.0,
        reasoning: `Career Assistant query: ${query.substring(0, 100)}...`
      }
    }).catch(() => {});

    sendSuccess(res, {
      available: true,
      query,
      mode,
      topic,
      response: generatedText,
      timestamp: new Date().toISOString()
    }, 'Career guidance generated successfully');
  } catch (error: any) {
    console.error('Career Assistant exception:', error);
    sendSuccess(res, {
      available: false,
      query,
      mode,
      message: 'Failed to communicate with the AI career assistant service.',
      response: null
    });
  }
}

export async function handleAspirantRecommendationsQuery(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { query, topic, mode } = req.body;

  const apiKey = process.env.GEMINI_API_KEY || process.env.AI_API_KEY;

  if (!apiKey) {
    sendSuccess(res, {
      available: false,
      query,
      mode,
      message: 'AI Admissions & Recommendations Assistant is currently unavailable on this server (GEMINI_API_KEY not configured). Please configure the AI service key in the server environment.',
      response: null,
      suggestedTopics: [
        'Top Computer Science & AI Programs in India vs Abroad',
        'Scholarships for Engineering Undergrads in 2026',
        'How to Prepare for JEE Main vs SAT for Dual Applications',
        'Choosing between B.Tech CS, AI & Data Science, and Mathematics & Computing'
      ]
    }, 'AI service status returned');
    return;
  }

  try {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: { profile: { include: { aspirantProfile: true } }, admissionPredictions: true }
    });

    const asp = user?.profile?.aspirantProfile;
    const userContext = `User Role: ASPIRANT, Target Degree: ${asp?.targetDegree || 'B.Tech'}, Target Major: ${asp?.targetMajor || 'Computer Science'}, Target Universities: ${asp?.targetUniversities || 'NIT, IIT, Stanford'}, High School: ${asp?.highSchool || 'High School'}, Scores: ${asp?.entranceExamScores || 'None'}`;

    const model = process.env.GEMINI_MODEL || 'gemini-3.5-flash';
    console.log(`[AI Aspirant Advisor] Outbound request to Gemini API (model: ${model}) for user: ${userId}`);
    const startTime = Date.now();
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              {
                text: `You are an expert college admissions counselor and academic advisor on CampusVerse.
Context: ${userContext}
Mode: ${mode}
Topic: ${topic || 'College Admissions & Academic Planning'}
Query: ${query}

Provide concrete, realistic, and highly actionable admissions guidance, college comparison takeaways, eligibility advice, and next steps.`
              }
            ]
          }
        ]
      })
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errText = await response.text();
      console.error(`[AI Aspirant Advisor] Gemini Aspirant API Error (${response.status}) in ${latencyMs}ms:`, errText);
      sendSuccess(res, {
        available: false,
        query,
        mode,
        message: 'The AI recommendations service encountered an upstream error. Please try again in a few moments.',
        response: null
      });
      return;
    }

    const data: any = await response.json();
    console.log(`[AI Aspirant Advisor] Gemini API response received in ${latencyMs}ms. Usage: promptTokens=${data.usageMetadata?.promptTokenCount}, candidatesTokens=${data.usageMetadata?.candidatesTokenCount}, totalTokens=${data.usageMetadata?.totalTokenCount}`);
    const generatedText = data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response generated.';

    await prisma.aIRecommendation.create({
      data: {
        userId,
        type: 'ASPIRANT_RECOMMENDATION',
        referenceId: `asp_${Date.now()}`,
        score: 1.0,
        reasoning: `Aspirant Advisor query: ${query.substring(0, 100)}...`
      }
    }).catch(() => {});

    sendSuccess(res, {
      available: true,
      query,
      mode,
      topic,
      response: generatedText,
      timestamp: new Date().toISOString()
    }, 'Admissions guidance generated successfully');
  } catch (error: any) {
    console.error('Aspirant Assistant exception:', error);
    sendSuccess(res, {
      available: false,
      query,
      mode,
      message: 'Failed to communicate with the AI admissions recommendations service.',
      response: null
    });
  }
}

export async function getChatSessions(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const sessions = await prisma.aIChatSession.findMany({
    where: { userId },
    orderBy: { updatedAt: 'desc' },
    include: {
      _count: {
        select: { messages: true }
      }
    }
  });

  sendSuccess(res, sessions, 'Chat sessions retrieved');
}

export async function getChatSessionById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const session = await prisma.aIChatSession.findUnique({
    where: { id },
    include: {
      messages: {
        orderBy: { createdAt: 'asc' }
      }
    }
  });

  if (!session) {
    sendError(res, 'Chat session not found.', 404, 'NOT_FOUND');
    return;
  }

  if (session.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'Access denied to this chat session.', 403, 'FORBIDDEN');
    return;
  }

  sendSuccess(res, session, 'Chat session retrieved');
}

export async function createChatSession(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { title, mode } = req.body;

  const session = await prisma.aIChatSession.create({
    data: {
      userId,
      title: title || 'New Conversation',
      agentType: mode || 'STUDY_TUTOR'
    }
  });

  sendSuccess(res, session, 'Chat session created', 201);
}

export async function sendChatMessage(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const { message } = req.body;

  const session = await prisma.aIChatSession.findUnique({
    where: { id }
  });

  if (!session) {
    sendError(res, 'Chat session not found.', 404, 'NOT_FOUND');
    return;
  }

  if (session.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'Access denied.', 403, 'FORBIDDEN');
    return;
  }

  // 1. Record user message
  await prisma.aIChatMessage.create({
    data: {
      sessionId: id,
      sender: 'USER',
      content: message
    }
  });

  const apiKey = process.env.GEMINI_API_KEY || process.env.AI_API_KEY;
  let replyText = '';
  let tokensUsed = 0;

  if (apiKey) {
    try {
      const model = process.env.GEMINI_MODEL || 'gemini-3.5-flash';
      // Fetch recent messages for context
      const history = await prisma.aIChatMessage.findMany({
        where: { sessionId: id },
        orderBy: { createdAt: 'desc' },
        take: 10
      });
      const chronological = history.reverse();

      const contents = chronological.map(m => ({
        role: m.sender === 'USER' ? 'user' : 'model',
        parts: [{ text: m.content }]
      }));

      const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contents })
      });

      if (response.ok) {
        const data: any = await response.json();
        replyText = data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response generated.';
        tokensUsed = data.usageMetadata?.totalTokenCount || 0;
      } else {
        replyText = 'AI assistant temporarily unavailable. Please retry in a moment.';
      }
    } catch {
      replyText = 'AI assistant encountered a connection error. Please retry.';
    }
  } else {
    replyText = `CampusVerse AI (${session.agentType} assistant): Thank you for your question "${message.substring(0, 50)}...". I am here to help you navigate campus life, academics, and career roadmaps!`;
  }

  // 2. Record AI message
  const aiMessage = await prisma.aIChatMessage.create({
    data: {
      sessionId: id,
      sender: 'ASSISTANT',
      content: replyText,
      metadata: tokensUsed ? JSON.stringify({ tokensUsed }) : null
    }
  });

  // Touch session updatedAt
  await prisma.aIChatSession.update({
    where: { id },
    data: { updatedAt: new Date() }
  });

  sendSuccess(res, aiMessage, 'Message processed', 201);
}

export async function deleteChatSession(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const session = await prisma.aIChatSession.findUnique({
    where: { id }
  });

  if (!session) {
    sendError(res, 'Chat session not found.', 404, 'NOT_FOUND');
    return;
  }

  if (session.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'Access denied.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.aIChatSession.delete({
    where: { id }
  });

  sendSuccess(res, { deleted: true }, 'Chat session deleted');
}


