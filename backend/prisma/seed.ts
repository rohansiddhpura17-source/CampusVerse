import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';
import { seedProducts } from '../src/scripts/seed-products';

const prisma = new PrismaClient();

async function main() {
  if (process.env.NODE_ENV === 'production') {
    console.error('CRITICAL ERROR: Seed script execution is strictly FORBIDDEN in PRODUCTION environment.');
    console.error('Seeding development test accounts with default passwords (Password123) is disallowed in production.');
    process.exit(1);
  }

  console.log('Seeding development database with rich Student, Alumni & Aspirant datasets...');

  // Clean existing records in reverse dependency order
  await prisma.admissionPrediction.deleteMany();
  await prisma.savedScholarship.deleteMany();
  await prisma.savedCollege.deleteMany();
  await prisma.scholarship.deleteMany();
  await prisma.collegeProgram.deleteMany();
  await prisma.auditLog.deleteMany();
  await prisma.aIRecommendation.deleteMany();
  await prisma.interviewSession.deleteMany();
  await prisma.skillProgress.deleteMany();
  await prisma.careerRoadmap.deleteMany();
  await prisma.careerPreference.deleteMany();
  await prisma.savedAlumni.deleteMany();
  await prisma.savedJob.deleteMany();
  await prisma.userConnection.deleteMany();
  await prisma.report.deleteMany();
  await prisma.announcement.deleteMany();
  await prisma.notification.deleteMany();
  await prisma.referral.deleteMany();
  await prisma.jobApplication.deleteMany();
  await prisma.job.deleteMany();
  await prisma.company.deleteMany();
  await prisma.message.deleteMany();
  await prisma.conversationParticipant.deleteMany();
  await prisma.conversation.deleteMany();
  await prisma.mentorshipSession.deleteMany();
  await prisma.mentorshipRequest.deleteMany();
  await prisma.mentorProfile.deleteMany();
  await prisma.marketplaceTransaction.deleteMany();
  await prisma.marketplaceItem.deleteMany();
  await prisma.communityComment.deleteMany();
  await prisma.communityPost.deleteMany();
  await prisma.communityMember.deleteMany();
  await prisma.community.deleteMany();
  await prisma.eventRegistration.deleteMany();
  await prisma.event.deleteMany();
  await prisma.libraryItem.deleteMany();
  await prisma.note.deleteMany();
  await prisma.course.deleteMany();
  await prisma.institution.deleteMany();
  await prisma.verification.deleteMany();
  await prisma.adminProfile.deleteMany();
  await prisma.alumniProfile.deleteMany();
  await prisma.studentProfile.deleteMany();
  await prisma.aspirantProfile.deleteMany();
  await prisma.privacySettings.deleteMany();
  await prisma.securitySettings.deleteMany();
  await prisma.refund.deleteMany();
  await prisma.entitlement.deleteMany();
  await prisma.transaction.deleteMany();
  await prisma.paymentOrder.deleteMany();
  await prisma.product.deleteMany();
  await prisma.webhookEvent.deleteMany();
  await prisma.profile.deleteMany();
  await prisma.user.deleteMany();

  const passwordHash = await bcrypt.hash('Password123', 10);

  // 1. Institutions & Colleges
  const inst1 = await prisma.institution.create({
    data: {
      name: 'National Institute of Technology',
      code: 'NIT-MAIN',
      domain: 'nit.edu',
      city: 'Bangalore',
      state: 'Karnataka',
      country: 'India',
      verified: true,
      ranking: 12,
      acceptanceRate: 4.5,
      averageFees: '₹2,20,000 / year',
      overview: 'Premier national institute recognized for world-class technical education, high-impact research, and industry placements.',
      campusSize: '300 Acres',
      websiteUrl: 'https://nit.edu'
    }
  });

  const inst2 = await prisma.institution.create({
    data: {
      name: 'Indian Institute of Technology Bombay',
      code: 'IITB',
      domain: 'iitb.ac.in',
      city: 'Mumbai',
      state: 'Maharashtra',
      country: 'India',
      verified: true,
      ranking: 1,
      acceptanceRate: 1.2,
      averageFees: '₹2,50,000 / year',
      overview: 'Top-ranked engineering institution in India known for cutting-edge computer science, research, and startup incubation.',
      campusSize: '550 Acres',
      websiteUrl: 'https://iitb.ac.in'
    }
  });

  const inst3 = await prisma.institution.create({
    data: {
      name: 'Stanford University',
      code: 'STANFORD',
      domain: 'stanford.edu',
      city: 'Stanford',
      state: 'California',
      country: 'United States',
      verified: true,
      ranking: 2,
      acceptanceRate: 3.9,
      averageFees: '$62,000 / year',
      overview: 'World-renowned research university in Silicon Valley, producing tech leaders, entrepreneurs, and Nobel laureates.',
      campusSize: '8,180 Acres',
      websiteUrl: 'https://stanford.edu'
    }
  });

  const inst4 = await prisma.institution.create({
    data: {
      name: 'Massachusetts Institute of Technology',
      code: 'MIT-US',
      domain: 'mit.edu',
      city: 'Cambridge',
      state: 'Massachusetts',
      country: 'United States',
      verified: true,
      ranking: 1,
      acceptanceRate: 4.1,
      averageFees: '$59,750 / year',
      overview: 'Global leader in science, engineering, robotics, and artificial intelligence research.',
      campusSize: '168 Acres',
      websiteUrl: 'https://mit.edu'
    }
  });

  const inst5 = await prisma.institution.create({
    data: {
      name: 'BITS Pilani',
      code: 'BITS-PILANI',
      domain: 'bits-pilani.ac.in',
      city: 'Pilani',
      state: 'Rajasthan',
      country: 'India',
      verified: true,
      ranking: 18,
      acceptanceRate: 6.8,
      averageFees: '₹5,40,000 / year',
      overview: 'Distinguished private institute renowned for its flexible curriculum, Practice School, and vibrant entrepreneurial ecosystem.',
      campusSize: '328 Acres',
      websiteUrl: 'https://bits-pilani.ac.in'
    }
  });

  const inst6 = await prisma.institution.create({
    data: {
      name: 'University of Toronto',
      code: 'UTORONTO',
      domain: 'utoronto.ca',
      city: 'Toronto',
      state: 'Ontario',
      country: 'Canada',
      verified: true,
      ranking: 21,
      acceptanceRate: 43.0,
      averageFees: 'CAD 60,000 / year',
      overview: 'Canada top public research university with leading deep learning and health science institutes.',
      campusSize: '180 Acres',
      websiteUrl: 'https://utoronto.ca'
    }
  });

  // College Programs
  await prisma.collegeProgram.createMany({
    data: [
      {
        institutionId: inst1.id,
        name: 'B.Tech in Computer Science and Engineering',
        degree: 'B.TECH',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: '₹2,20,000 / year',
        minGpa: 8.0,
        entranceExams: JSON.stringify(['JEE_MAIN', 'JEE_ADVANCED']),
        deadline: '2026-06-30',
        overview: 'Comprehensive core curriculum covering data structures, OS, distributed systems, and AI.'
      },
      {
        institutionId: inst1.id,
        name: 'B.Tech in Artificial Intelligence & Data Science',
        degree: 'B.TECH',
        major: 'Artificial Intelligence',
        durationYears: 4.0,
        tuitionFee: '₹2,40,000 / year',
        minGpa: 8.5,
        entranceExams: JSON.stringify(['JEE_MAIN']),
        deadline: '2026-06-30',
        overview: 'Specialized track in deep learning, NLP, computer vision, and big data architecture.'
      },
      {
        institutionId: inst2.id,
        name: 'B.Tech in Computer Science & Engineering',
        degree: 'B.TECH',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: '₹2,50,000 / year',
        minGpa: 9.0,
        entranceExams: JSON.stringify(['JEE_ADVANCED']),
        deadline: '2026-06-15',
        overview: 'Premier engineering program in India with world-class faculty and global placements.'
      },
      {
        institutionId: inst2.id,
        name: 'B.Tech in Electrical Engineering',
        degree: 'B.TECH',
        major: 'Electrical Engineering',
        durationYears: 4.0,
        tuitionFee: '₹2,50,000 / year',
        minGpa: 8.5,
        entranceExams: JSON.stringify(['JEE_ADVANCED']),
        deadline: '2026-06-15',
        overview: 'Advanced VLSI, microelectronics, signal processing, and robotics curriculum.'
      },
      {
        institutionId: inst3.id,
        name: 'BS in Computer Science',
        degree: 'BS',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: '$62,000 / year',
        minGpa: 3.9,
        entranceExams: JSON.stringify(['SAT', 'ACT', 'TOEFL']),
        deadline: '2026-01-05',
        overview: 'Pioneering CS department with concentrations in systems, AI, theory, and human-computer interaction.'
      },
      {
        institutionId: inst4.id,
        name: 'BS in Electrical Engineering & Computer Science (Course 6-3)',
        degree: 'BS',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: '$59,750 / year',
        minGpa: 4.0,
        entranceExams: JSON.stringify(['SAT', 'TOEFL']),
        deadline: '2026-01-01',
        overview: 'Intensive engineering and computer science program at MIT CSAIL.'
      },
      {
        institutionId: inst5.id,
        name: 'B.E. in Computer Science',
        degree: 'B.TECH',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: '₹5,40,000 / year',
        minGpa: 8.2,
        entranceExams: JSON.stringify(['BITSAT', 'SAT']),
        deadline: '2026-06-20',
        overview: 'Renowned zero-attendance policy with 6-month industrial Practice School internships.'
      },
      {
        institutionId: inst6.id,
        name: 'B.Sc in Computer Science (Specialist)',
        degree: 'BSC',
        major: 'Computer Science',
        durationYears: 4.0,
        tuitionFee: 'CAD 60,000 / year',
        minGpa: 3.7,
        entranceExams: JSON.stringify(['IELTS', 'TOEFL']),
        deadline: '2026-01-15',
        overview: 'Home of the Vector Institute for AI with ASIP co-op work-term options.'
      }
    ]
  });

  // Scholarships
  const s1 = await prisma.scholarship.create({
    data: {
      name: 'National Merit STEM Undergraduate Grant',
      provider: 'Ministry of Education & Science',
      amount: '₹2,50,000 / year',
      deadline: '2026-07-31',
      eligibility: 'Class 12 STEM score >= 90% or JEE Main percentile >= 95.0. Family income < ₹8 LPA.',
      description: 'Prestigious national grant aimed at supporting high-achieving undergraduate students in technical disciplines.',
      requirements: JSON.stringify(['Class 12 Marksheet', 'JEE Main Scorecard', 'Income Certificate']),
      applicationUrl: 'https://scholarships.gov.in/stem-grant',
      category: 'MERIT',
      country: 'India'
    }
  });

  const s2 = await prisma.scholarship.create({
    data: {
      name: 'Tata Trust Higher Education Grant',
      provider: 'Tata Trusts India',
      amount: '₹1,80,000 / year',
      deadline: '2026-08-15',
      eligibility: 'Enrolled or admitted to recognized technical institutions. Minimum 85% in Class 12.',
      description: 'Merit-cum-need financial support for deserving engineering and applied science undergraduates.',
      requirements: JSON.stringify(['College Admission Letter', 'Academic Transcripts', 'Statement of Purpose']),
      applicationUrl: 'https://tatatrusts.org/education-grants',
      category: 'NEED_BASED',
      country: 'India'
    }
  });

  const s3 = await prisma.scholarship.create({
    data: {
      name: 'Google Women Techmakers Scholarship',
      provider: 'Google Inc.',
      amount: '$10,000 (One-time)',
      deadline: '2026-05-30',
      eligibility: 'Female students pursuing undergraduate or graduate degrees in Computer Science or related fields.',
      description: 'Empowering women in tech through academic funding, mentorship, and invitation to the annual Google Retreat.',
      requirements: JSON.stringify(['Resume', 'Academic Transcript', 'Technical Essay Responses']),
      applicationUrl: 'https://buildyourfuture.withgoogle.com/scholarships/women-techmakers',
      category: 'WOMEN_IN_TECH',
      country: 'United States'
    }
  });

  const s4 = await prisma.scholarship.create({
    data: {
      name: 'Reliance Foundation Undergraduate Scholarship',
      provider: 'Reliance Foundation',
      amount: '₹2,00,000 / year',
      deadline: '2026-10-15',
      eligibility: 'First-year undergraduate students in any degree stream with minimum 60% in Class 12.',
      description: 'Comprehensive scholarship supporting talented youth to pursue higher education and leadership development.',
      requirements: JSON.stringify(['Aptitude Test Score', 'Class 12 Certificate', 'College ID / Admission Slip']),
      applicationUrl: 'https://scholarships.reliancefoundation.org',
      category: 'MERIT',
      country: 'India'
    }
  });

  const s5 = await prisma.scholarship.create({
    data: {
      name: 'Global Leaders International Fellowship',
      provider: 'Global Education Council',
      amount: '$25,000 / year',
      deadline: '2026-03-15',
      eligibility: 'International students admitted to top 50 ranked global universities with SAT >= 1450 or GRE >= 320.',
      description: 'Flagship grant providing substantial tuition support for high-impact international students.',
      requirements: JSON.stringify(['Offer Letter', 'Standardized Test Scores', '2 Letters of Recommendation']),
      applicationUrl: 'https://globalleaders.edu/fellowship',
      category: 'INTERNATIONAL',
      country: 'United States'
    }
  });

  const s6 = await prisma.scholarship.create({
    data: {
      name: 'Fulbright Foreign Student Fellowship',
      provider: 'US-India Educational Foundation',
      amount: 'Full Tuition + Monthly Stipend',
      deadline: '2026-06-01',
      eligibility: 'Graduating seniors and young professionals with exceptional academic records applying to US graduate programs.',
      description: 'World-renowned bilateral cultural and academic exchange fellowship covering complete cost of study in the US.',
      requirements: JSON.stringify(['Full Application Portfolio', 'Research Proposal', 'IELTS / TOEFL Scorecard']),
      applicationUrl: 'https://usief.org.in/Fulbright-Fellowships.aspx',
      category: 'INTERNATIONAL',
      country: 'United States'
    }
  });

  // 2. Courses & Library for NIT
  const course1 = await prisma.course.create({
    data: {
      institutionId: inst1.id,
      code: 'CS301',
      name: 'Distributed Systems & Cloud Computing',
      department: 'Computer Science',
      credits: 4,
      semester: 6,
      description: 'Consensus algorithms, replication, and microservices architecture'
    }
  });

  const libraryItem1 = await prisma.libraryItem.create({
    data: {
      institutionId: inst1.id,
      title: 'Introduction to Algorithms (CLRS) 4th Edition',
      author: 'Thomas H. Cormen, Charles E. Leiserson',
      isbn: '978-0262046305',
      category: 'EBOOK',
      location: 'Digital Shelf / Section A',
      totalCopies: 5,
      availableCopies: 3
    }
  });

  // 3. Primary Users
  // Primary Student
  const studentUser = await prisma.user.create({
    data: {
      email: 'student@campusverse.edu',
      passwordHash,
      role: 'STUDENT',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Aarav Sharma',
          headline: 'CS Junior @ NIT | Android & Cloud Enthusiast',
          bio: 'Passionate about mobile apps, Jetpack Compose, and distributed systems.',
          location: 'Bengaluru, India',
          phone: '+91 98765 43210',
          linkedin: 'https://linkedin.com/in/aarav-sharma-demo',
          github: 'https://github.com/aaravsharma',
          studentProfile: {
            create: {
              institutionId: inst1.id,
              studentIdNumber: '2023CSB1042',
              degree: 'B.Tech',
              major: 'Computer Science and Engineering',
              semester: 6,
              cgpa: 8.75,
              graduationYear: 2027
            }
          }
        }
      },
      privacySettings: { create: { allowMessagesFrom: 'ALL', allowMentorshipRequests: true } },
      securitySettings: { create: { loginAlertsEnabled: true, twoFactorEnabled: false } }
    }
  });

  // Primary Alumni
  const alumniUser = await prisma.user.create({
    data: {
      email: 'alumni@campusverse.edu',
      passwordHash,
      role: 'ALUMNI',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Priya Patel',
          headline: 'Senior Software Engineer @ Google | NIT Alumna 2021',
          bio: 'Building large-scale Android platforms and cloud services. Passionate about mentoring students and aspiring engineers.',
          location: 'Hyderabad, India',
          phone: '+91 98765 43211',
          linkedin: 'https://linkedin.com/in/priya-patel-google',
          website: 'https://priyapatel.dev',
          alumniProfile: {
            create: {
              institutionId: inst1.id,
              degree: 'B.Tech Computer Science',
              graduationYear: 2021,
              currentCompany: 'Google India',
              currentDesignation: 'Senior Software Engineer',
              industry: 'Technology & Cloud',
              yearsOfExperience: 5,
              willingToMentor: true,
              willingToRefer: true
            }
          }
        }
      },
      privacySettings: { create: { allowMessagesFrom: 'ALL', allowMentorshipRequests: true, showEmail: false, showPhone: true } },
      securitySettings: { create: { loginAlertsEnabled: true, twoFactorEnabled: true } },
      mentorProfile: {
        create: {
          title: 'Senior Software Engineer & Career Mentor',
          company: 'Google India',
          expertise: JSON.stringify(['Android', 'Kotlin', 'System Design', 'Algorithms', 'Career Growth']),
          hourlyRate: 0,
          maxMentees: 8,
          isAcceptingMentees: true,
          bio: 'Ex-Amazon, now leading Android infrastructure at Google. Open to 1:1 resume reviews, mock interviews, and career guidance.',
          rating: 4.95,
          reviewsCount: 24
        }
      }
    }
  });

  // Alumni Peer 1: Vikram Mehta
  const alumniPeer1 = await prisma.user.create({
    data: {
      email: 'vikram.mehta@alumni.nit.edu',
      passwordHash,
      role: 'ALUMNI',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Vikram Mehta',
          headline: 'Principal Engineer @ Microsoft | Ex-Oracle',
          bio: 'Distributed databases and large-scale cloud infrastructure specialist.',
          location: 'Hyderabad, India',
          phone: '+91 98765 43212',
          linkedin: 'https://linkedin.com/in/vikram-mehta-microsoft',
          alumniProfile: {
            create: {
              institutionId: inst1.id,
              degree: 'B.Tech Computer Science',
              graduationYear: 2017,
              currentCompany: 'Microsoft IDC',
              currentDesignation: 'Principal Engineer',
              industry: 'Cloud Infrastructure',
              yearsOfExperience: 8,
              willingToMentor: true,
              willingToRefer: true
            }
          }
        }
      },
      privacySettings: { create: {} },
      securitySettings: { create: {} },
      mentorProfile: {
        create: {
          title: 'Principal Systems Architect & Mentor',
          company: 'Microsoft IDC',
          expertise: JSON.stringify(['System Design', 'Cloud Architecture', 'Azure', 'Kubernetes', 'Scalability']),
          hourlyRate: 0,
          maxMentees: 5,
          isAcceptingMentees: true,
          bio: 'Specializing in L6/L7 system design rounds and distributed data architectures.',
          rating: 4.98,
          reviewsCount: 31
        }
      }
    }
  });

  // Alumni Peer 2: Neha Kapoor
  const alumniPeer2 = await prisma.user.create({
    data: {
      email: 'neha.kapoor@alumni.nit.edu',
      passwordHash,
      role: 'ALUMNI',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Neha Kapoor',
          headline: 'Engineering Manager @ Uber | Ex-Flipkart',
          bio: 'Leading mobility tech engineering teams. Coaching engineers on leadership, career progression, and behavioral interviews.',
          location: 'Bengaluru, India',
          phone: '+91 98765 43213',
          linkedin: 'https://linkedin.com/in/neha-kapoor-uber',
          alumniProfile: {
            create: {
              institutionId: inst1.id,
              degree: 'B.Tech Electronics & CS',
              graduationYear: 2018,
              currentCompany: 'Uber India',
              currentDesignation: 'Engineering Manager',
              industry: 'Ride Sharing & Mobility',
              yearsOfExperience: 7,
              willingToMentor: true,
              willingToRefer: true
            }
          }
        }
      },
      privacySettings: { create: {} },
      securitySettings: { create: {} },
      mentorProfile: {
        create: {
          title: 'Engineering Leadership Coach',
          company: 'Uber India',
          expertise: JSON.stringify(['Engineering Management', 'Behavioral Interviews', 'STAR Method', 'Career Transitions']),
          hourlyRate: 0,
          maxMentees: 4,
          isAcceptingMentees: true,
          bio: 'Helping engineers make the transition to Tech Lead and Management roles.',
          rating: 4.92,
          reviewsCount: 19
        }
      }
    }
  });

  // Alumni Peer 3: Aditya Verma
  const alumniPeer3 = await prisma.user.create({
    data: {
      email: 'aditya.verma@alumni.nit.edu',
      passwordHash,
      role: 'ALUMNI',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Aditya Verma',
          headline: 'Solutions Architect @ Amazon AWS',
          bio: 'Helping global enterprises build cloud-native distributed backends.',
          location: 'Bengaluru, India',
          phone: '+91 98765 43214',
          linkedin: 'https://linkedin.com/in/aditya-verma-aws',
          alumniProfile: {
            create: {
              institutionId: inst1.id,
              degree: 'B.Tech CS',
              graduationYear: 2020,
              currentCompany: 'Amazon AWS',
              currentDesignation: 'Solutions Architect',
              industry: 'Cloud & DevOps',
              yearsOfExperience: 4,
              willingToMentor: true,
              willingToRefer: true
            }
          }
        }
      },
      privacySettings: { create: {} },
      securitySettings: { create: {} }
    }
  });

  // Primary Aspirant User: Rohan Mehta
  const aspirantUser = await prisma.user.create({
    data: {
      email: 'aspirant@campusverse.edu',
      passwordHash,
      role: 'ASPIRANT',
      isEmailVerified: true,
      profile: {
        create: {
          fullName: 'Rohan Mehta',
          headline: 'Aspiring CS Undergrad | Class of 2027 Prospect',
          bio: 'Exploring top-tier engineering institutions, scholarship opportunities, and campus life.',
          location: 'Mumbai, India',
          aspirantProfile: {
            create: {
              targetDegree: 'B.Tech',
              targetMajor: 'Computer Science & AI',
              targetUniversities: 'IIT Bombay, NIT Bangalore, BITS Pilani, Stanford University',
              highSchool: 'Delhi Public School, Mumbai',
              expectedGradYear: 2027,
              entranceExamScores: JSON.stringify({
                JEE_MAIN: 98.4,
                SAT: 1490,
                GPA: 9.2
              })
            }
          }
        }
      },
      privacySettings: { create: { allowMessagesFrom: 'ALL', allowMentorshipRequests: true } },
      securitySettings: { create: { loginAlertsEnabled: true, twoFactorEnabled: false } }
    }
  });

  // Saved Colleges for Aspirant
  await prisma.savedCollege.createMany({
    data: [
      { userId: aspirantUser.id, institutionId: inst1.id },
      { userId: aspirantUser.id, institutionId: inst2.id },
      { userId: aspirantUser.id, institutionId: inst3.id }
    ]
  });

  // Saved Scholarships for Aspirant
  await prisma.savedScholarship.createMany({
    data: [
      { userId: aspirantUser.id, scholarshipId: s1.id },
      { userId: aspirantUser.id, scholarshipId: s2.id }
    ]
  });

  // Admission Predictions for Aspirant
  await prisma.admissionPrediction.createMany({
    data: [
      {
        userId: aspirantUser.id,
        institutionId: inst1.id,
        institutionName: 'National Institute of Technology',
        programName: 'B.Tech in Computer Science and Engineering',
        degree: 'B.TECH',
        gpa: 9.2,
        testType: 'JEE_MAIN',
        testScore: 98.4,
        predictionPercentage: 94.5,
        qualificationStatus: 'STRONG_CANDIDATE',
        feedback: 'Your 98.4 percentile in JEE Main and 9.2 GPA significantly exceed the historical cutoff (95.0 percentile) for NIT Bangalore CSE.',
        recommendations: JSON.stringify([
          'Prepare for JoSAA counseling round 1 preference lock',
          'Explore NIT merit scholarships to offset tuition',
          'Review campus placement statistics for software development roles'
        ])
      },
      {
        userId: aspirantUser.id,
        institutionId: inst2.id,
        institutionName: 'Indian Institute of Technology Bombay',
        programName: 'B.Tech in Computer Science & Engineering',
        degree: 'B.TECH',
        gpa: 9.2,
        testType: 'JEE_MAIN',
        testScore: 98.4,
        predictionPercentage: 76.8,
        qualificationStatus: 'COMPETITIVE',
        feedback: 'You have a competitive profile for IIT Bombay. Performance in JEE Advanced will be the determining factor for final seat allocation.',
        recommendations: JSON.stringify([
          'Focus on JEE Advanced problem-solving speed in Physics & Mathematics',
          'Target top 500 All India Rank in JEE Advanced',
          'Keep NIT Bangalore and BITS Pilani as solid safety choices'
        ])
      },
      {
        userId: aspirantUser.id,
        institutionId: inst3.id,
        institutionName: 'Stanford University',
        programName: 'BS in Computer Science',
        degree: 'BS',
        gpa: 9.2,
        testType: 'SAT',
        testScore: 1490.0,
        predictionPercentage: 38.5,
        qualificationStatus: 'REACH',
        feedback: 'Stanford has a highly selective 3.9% acceptance rate. Your 1490 SAT and GPA meet the threshold, but compelling extracurriculars and essays are required.',
        recommendations: JSON.stringify([
          'Strengthen extracurricular leadership and open-source project portfolio',
          'Craft unique Common App personal essays demonstrating vision',
          'Secure 2 strong recommendation letters from STEM instructors'
        ])
      }
    ]
  });

  // Admin User
  const adminUser = await prisma.user.create({
    data: {
      email: 'admin@campusverse.edu',
      passwordHash,
      role: 'ADMIN',
      isEmailVerified: true,
      isAdminAuthorized: true,
      profile: {
        create: {
          fullName: 'Dr. Vikram Sen',
          headline: 'Dean of Student Affairs & Chief Platform Administrator',
          bio: 'Supervising campus operations and verification compliance.',
          adminProfile: {
            create: {
              department: 'Administration & Student Welfare',
              accessLevel: 'SUPERADMIN',
              employeeId: 'ADM-9021'
            }
          }
        }
      },
      privacySettings: { create: {} },
      securitySettings: { create: {} }
    }
  });

  // Notes & Communities
  await prisma.note.create({
    data: {
      userId: studentUser.id,
      courseId: course1.id,
      title: 'Distributed Systems Complete Raft Notes',
      description: 'Detailed notes on Raft consensus algorithm with visual diagrams.',
      fileUrl: 'https://docs.campusverse.edu/notes/raft_consensus.pdf',
      tags: 'distributed-systems,raft,cse',
      isPublic: true,
      downloadsCount: 42
    }
  });

  const community1 = await prisma.community.create({
    data: {
      name: 'Android & Mobile Developers',
      slug: 'android-mobile-devs',
      description: 'Community for Android developers building with Jetpack Compose and Kotlin.',
      creatorId: alumniUser.id,
      memberCount: 3,
      members: {
        create: [
          { userId: alumniUser.id, role: 'ADMIN' },
          { userId: studentUser.id, role: 'MEMBER' },
          { userId: aspirantUser.id, role: 'MEMBER' }
        ]
      }
    }
  });

  await prisma.communityPost.create({
    data: {
      communityId: community1.id,
      authorId: studentUser.id,
      title: 'Architecting Jetpack Compose apps with MVI and DataStore',
      content: 'Sharing best practices on managing state with StateFlow and offline caching in Android.',
      likesCount: 14,
      commentsCount: 1,
      comments: {
        create: {
          authorId: alumniUser.id,
          content: 'Great overview Aarav! Make sure to also test session restoration during cold starts.'
        }
      }
    }
  });

  await prisma.marketplaceItem.create({
    data: {
      sellerId: studentUser.id,
      title: 'CLRS Introduction to Algorithms (4th Edition - Hardcover)',
      description: 'Gently used algorithms textbook, perfect for CS semester 3 & 4. No annotations.',
      price: 950.0,
      category: 'TEXTBOOK',
      condition: 'LIKE_NEW',
      status: 'AVAILABLE'
    }
  });

  // User Connections
  await prisma.userConnection.createMany({
    data: [
      { requesterId: alumniUser.id, receiverId: alumniPeer1.id, status: 'ACCEPTED' },
      { requesterId: alumniUser.id, receiverId: alumniPeer2.id, status: 'ACCEPTED' },
      { requesterId: alumniPeer3.id, receiverId: alumniUser.id, status: 'PENDING' },
      { requesterId: studentUser.id, receiverId: alumniUser.id, status: 'ACCEPTED' }
    ]
  });

  // Saved Alumni
  await prisma.savedAlumni.create({
    data: {
      userId: alumniUser.id,
      alumniId: alumniPeer1.id
    }
  });

  // Companies & Jobs
  const googleCompany = await prisma.company.create({
    data: {
      name: 'Google India',
      website: 'https://careers.google.com',
      industry: 'Technology & Cloud',
      description: 'Global technology leader building products for billions of users across Android, Cloud, Search, and AI.',
      logoUrl: 'https://images.unsplash.com/photo-1572021335469-31706a17aaef?w=150',
      verified: true
    }
  });

  const msftCompany = await prisma.company.create({
    data: {
      name: 'Microsoft IDC',
      website: 'https://careers.microsoft.com',
      industry: 'Cloud Infrastructure & Enterprise',
      description: 'Empowering every person and organization on the planet to achieve more with Azure, AI, and developer tooling.',
      logoUrl: 'https://images.unsplash.com/photo-1583321500900-82807e458f3c?w=150',
      verified: true
    }
  });

  const job1 = await prisma.job.create({
    data: {
      posterId: alumniUser.id,
      companyId: googleCompany.id,
      title: 'Senior Android Platform Engineer (L5)',
      description: 'Design and optimize core Android runtime frameworks and client architecture.',
      roleType: 'FULL_TIME',
      location: 'Hyderabad / Bengaluru, India',
      isRemote: true,
      salaryRange: '₹45,00,000 - ₹65,00,000',
      requirements: '5+ years experience with Kotlin, Jetpack Compose, Coroutines, and Android internals.',
      status: 'ACTIVE'
    }
  });

  const job2 = await prisma.job.create({
    data: {
      posterId: alumniPeer1.id,
      companyId: msftCompany.id,
      title: 'Principal Distributed Systems Architect (L6)',
      description: 'Lead engineering teams architecting high-throughput, low-latency Azure data services.',
      roleType: 'FULL_TIME',
      location: 'Hyderabad, India',
      isRemote: false,
      salaryRange: '₹70,00,000 - ₹95,00,000',
      requirements: '8+ years designing fault-tolerant distributed consensus systems and cloud backends.',
      status: 'ACTIVE'
    }
  });

  // Saved Jobs
  await prisma.savedJob.create({
    data: {
      userId: alumniUser.id,
      jobId: job2.id
    }
  });

  // Job Applications
  await prisma.jobApplication.create({
    data: {
      applicantId: alumniUser.id,
      jobId: job2.id,
      status: 'INTERVIEWING',
      resumeUrl: 'https://docs.campusverse.edu/resumes/priya_patel_staff.pdf',
      coverLetter: 'Bringing 5+ years of distributed systems and mobile platform architecture leadership.'
    }
  });

  // Referrals
  await prisma.referral.create({
    data: {
      alumniId: alumniUser.id,
      studentId: studentUser.id,
      jobId: job1.id,
      companyName: 'Google India',
      status: 'REQUESTED',
      notes: 'Aarav has built high-quality open source Kotlin libraries and demonstrated strong CS fundamentals.'
    }
  });

  // Mentorship Requests & Sessions
  const alumniMentorProfile = await prisma.mentorProfile.findFirst({ where: { userId: alumniUser.id } });
  const mReq1 = await prisma.mentorshipRequest.create({
    data: {
      mentorId: alumniMentorProfile!.id,
      menteeId: studentUser.id,
      status: 'ACCEPTED',
      goal: 'Android Architecture & Google Interview Preparation',
      message: 'Hi Priya, I am preparing for upcoming SWE internships and would love guidance on Clean Architecture in Compose.'
    }
  });

  await prisma.mentorshipSession.create({
    data: {
      requestId: mReq1.id,
      scheduledAt: new Date(Date.now() + 86400000 * 2), // 2 days from now
      durationMinutes: 45,
      meetingUrl: 'https://meet.google.com/xyz-cv-alumni',
      notes: 'Focus on MVI state management, mock coding question on binary trees, and resume review.',
      status: 'SCHEDULED'
    }
  });

  // Career Roadmaps & Skills for Alumni
  await prisma.careerRoadmap.create({
    data: {
      userId: alumniUser.id,
      title: 'Principal Engineer (L6 / Staff+) Mastery Track',
      targetRole: 'Principal Software Engineer',
      progressPercentage: 60.0,
      milestones: JSON.stringify([
        { id: 'm1', title: 'Author cross-organizational technical RFC for microservices migration', completed: true, targetQuarter: 'Q1' },
        { id: 'm2', title: 'Lead end-to-end multi-region active-active database failover deployment', completed: true, targetQuarter: 'Q2' },
        { id: 'm3', title: 'Mentor 3 Senior Engineers to Tech Lead promotions', completed: true, targetQuarter: 'Q3' },
        { id: 'm4', title: 'Deliver keynote on modern mobile architecture at global tech summit', completed: false, targetQuarter: 'Q4' }
      ])
    }
  });

  await prisma.skillProgress.createMany({
    data: [
      { userId: alumniUser.id, skillName: 'Distributed Systems & Consensus', category: 'ARCHITECTURE', level: 'EXPERT', verified: true, assessmentScore: 96.0 },
      { userId: alumniUser.id, skillName: 'Jetpack Compose & Kotlin Multiplatform', category: 'MOBILE', level: 'EXPERT', verified: true, assessmentScore: 98.0 },
      { userId: alumniUser.id, skillName: 'Kubernetes & Service Mesh', category: 'CLOUD', level: 'ADVANCED', verified: true, assessmentScore: 88.0 }
    ]
  });

  // Interview Sessions
  await prisma.interviewSession.create({
    data: {
      userId: alumniUser.id,
      roleTarget: 'Staff Software Engineer',
      topic: 'Global Distributed Rate Limiter & Token Bucket Algorithm',
      durationMinutes: 45,
      feedbackScore: 92.0,
      transcript: 'Detailed mock interview evaluation covering sliding-window counter, Redis clusters, and edge proxy caching.',
      strengths: JSON.stringify([
        'Superb clarity on CAP theorem tradeoffs and distributed lock avoidance',
        'Strong justification for Redis sliding logs and memory footprint calculations',
        'Excellent communication structured with STAR framework'
      ]),
      improvements: JSON.stringify([
        'Elaborate more on localized fallback circuits during regional network partitions',
        'Consider client-side rate limiting headers (X-RateLimit-Remaining) in API design'
      ])
    }
  });

  // Career Preferences
  await prisma.careerPreference.create({
    data: {
      userId: alumniUser.id,
      preferredRoles: JSON.stringify(['Staff Software Engineer', 'Engineering Manager', 'Tech Lead']),
      preferredLocations: JSON.stringify(['Hyderabad', 'Bengaluru', 'Remote (Global)']),
      targetSalary: '₹50,00,000 - ₹70,00,000',
      remotePreference: 'HYBRID',
      industries: JSON.stringify(['Technology', 'Cloud Infrastructure', 'AI & Developer Tools']),
      jobAlerts: true
    }
  });

  // Events
  await prisma.event.create({
    data: {
      institutionId: inst1.id,
      organizerId: alumniUser.id,
      title: 'Annual Campus Tech Hackathon 2026',
      description: '48-hour competitive software hackathon for students and prospective applicants.',
      category: 'HACKATHON',
      location: 'Main Innovation Center',
      isOnline: true,
      meetingUrl: 'https://meet.campusverse.edu/hackathon-2026',
      startTime: new Date(Date.now() + 86400000 * 3),
      endTime: new Date(Date.now() + 86400000 * 5),
      capacity: 300,
      registeredCount: 45,
      status: 'UPCOMING'
    }
  });

  // Conversations & Messages
  const conv1 = await prisma.conversation.create({
    data: {
      isGroup: false,
      title: 'Mentorship Chat: Priya & Aarav',
      participants: {
        create: [
          { userId: alumniUser.id },
          { userId: studentUser.id }
        ]
      }
    }
  });

  await prisma.message.createMany({
    data: [
      {
        conversationId: conv1.id,
        senderId: studentUser.id,
        content: 'Hi Priya! Thank you so much for accepting my mentorship request.',
        isRead: true
      },
      {
        conversationId: conv1.id,
        senderId: alumniUser.id,
        content: 'Hey Aarav, glad to connect! Let me know if you would like to go over system design or resume review in our session this week.',
        isRead: false
      }
    ]
  });

  // Notifications
  await prisma.notification.createMany({
    data: [
      {
        userId: aspirantUser.id,
        type: 'SYSTEM',
        title: 'Admission Predictor Updated',
        message: 'Your admission prediction for NIT Bangalore CSE has been calculated at 94.5% (Strong Candidate).',
        isRead: false
      },
      {
        userId: aspirantUser.id,
        type: 'SYSTEM',
        title: 'New Scholarship Matching Your Profile',
        message: 'National Merit STEM Undergraduate Grant (₹2,50,000/yr) applications are now open.',
        isRead: false
      },
      {
        userId: alumniUser.id,
        type: 'CONNECTION',
        title: 'New Connection Request',
        message: 'Aditya Verma (Solutions Architect @ Amazon AWS) sent you a connection request.',
        isRead: false
      }
    ]
  });

  await seedProducts();

  console.log('✅ Seeding completed successfully!');
  console.log('Demo Accounts:');
  console.log(' - Student:  student@campusverse.edu (Password: Password123)');
  console.log(' - Alumni:   alumni@campusverse.edu (Password: Password123)');
  console.log(' - Aspirant: aspirant@campusverse.edu (Password: Password123)');
  console.log(' - Admin:    admin@campusverse.edu (Password: Password123)');
}

main()
  .catch((e) => {
    console.error('Seeding error:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
