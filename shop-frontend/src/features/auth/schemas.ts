import { z } from "zod";

export const memberSchema = z.object({
  memberId: z.number(),
  loginId: z.string(),
  email: z.string().email().nullable().optional(),
  name: z.string(),
  birthDate: z.string(),
  age: z.number().int().nonnegative(),
  gender: z.enum(["FEMALE", "MALE", "OTHER", "PREFER_NOT_TO_SAY"]),
  phone: z.string(),
  postcode: z.string(),
  address1: z.string(),
  address2: z.string().nullable().optional(),
  role: z.enum(["CUSTOMER", "ADMIN"]),
});

export type Member = z.infer<typeof memberSchema>;

export const loginSchema = z.object({
  loginId: z.string().min(1, "아이디를 입력해 주세요."),
  password: z.string().min(1, "비밀번호를 입력해 주세요."),
});

export type LoginValues = z.infer<typeof loginSchema>;

const loginIdSchema = z
  .string()
  .regex(
    /^[a-zA-Z][a-zA-Z0-9_]{3,19}$/,
    "아이디는 영문으로 시작해 4~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다.",
  );

export const signupSchema = z
  .object({
    loginId: loginIdSchema,
    email: z
      .string()
      .trim()
      .refine((value) => value.length === 0 || z.string().email().safeParse(value).success, {
        message: "올바른 이메일 형식이 아닙니다.",
      }),
    password: z
      .string()
      .min(10, "비밀번호는 최소 10자여야 합니다.")
      .regex(/[A-Za-z]/, "영문을 포함해야 합니다.")
      .regex(/[0-9]/, "숫자를 포함해야 합니다.")
      .regex(/[^A-Za-z0-9]/, "특수문자를 포함해야 합니다."),
    passwordConfirm: z.string(),
    name: z.string().min(2, "이름은 2자 이상이어야 합니다.").max(100),
    birthDate: z.string().min(1, "생년월일을 입력해 주세요."),
    gender: z.enum(["FEMALE", "MALE", "OTHER", "PREFER_NOT_TO_SAY"]),
    phone: z.string().min(9, "연락처를 입력해 주세요.").max(32),
    postcode: z.string().min(1, "우편번호를 입력해 주세요.").max(16),
    address1: z.string().min(1, "기본주소를 입력해 주세요.").max(255),
    address2: z.string().max(255).optional(),
    termsAgreed: z.boolean().refine((v) => v === true, {
      message: "이용약관에 동의해 주세요.",
    }),
    privacyAgreed: z.boolean().refine((v) => v === true, {
      message: "개인정보 수집에 동의해 주세요.",
    }),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다.",
    path: ["passwordConfirm"],
  });

export type SignupValues = z.infer<typeof signupSchema>;
