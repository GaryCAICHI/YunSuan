#!/usr/bin/env python3
import argparse
import concurrent.futures
import pathlib
import re
import secrets
import subprocess


EXECUTED_RE = re.compile(r"HAS Executed Cycles:(\d+) Operations:(\d+)")


def run_one(emu, output_dir, fu_type, seed, count, index):
    log_path = output_dir / f"task-{index:03d}-seed-{seed}.log"
    command = [str(emu), "-s", str(seed), "-O", str(count), "--fu-type", str(fu_type)]
    with log_path.open("w", encoding="utf-8") as log_file:
        result = subprocess.run(command, stdout=log_file, stderr=subprocess.STDOUT, check=False)
    content = log_path.read_text(encoding="utf-8", errors="replace")
    match = EXECUTED_RE.search(content)
    executed = int(match.group(2)) if match else 0
    passed = (
        result.returncode == 0
        and "EMU EXCEEDED LIMIT" in content
        and "BADTRAP" not in content
        and "compare failed" not in content
        and executed == count
    )
    return index, seed, count, executed, passed, log_path


def main():
    parser = argparse.ArgumentParser(description="Run YunSuan random instruction tests in parallel")
    parser.add_argument("--fu-type", type=int, required=True, choices=(5, 10))
    parser.add_argument("--total", type=int, required=True)
    parser.add_argument("--jobs", type=int, required=True)
    parser.add_argument("--base-seed", type=int)
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--emu", type=pathlib.Path, default=pathlib.Path("build/emu"))
    args = parser.parse_args()

    if args.total <= 0 or args.jobs <= 0:
        parser.error("--total and --jobs must be positive")
    jobs = min(args.jobs, args.total)
    base_seed = args.base_seed if args.base_seed is not None else secrets.randbelow(2**31 - jobs)
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error(f"--output-dir must be empty: {args.output_dir}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    base, remainder = divmod(args.total, jobs)
    work = []
    for index in range(jobs):
        count = base + (1 if index < remainder else 0)
        work.append((args.emu.resolve(), args.output_dir, args.fu_type,
                     base_seed + index, count, index))

    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
        results = list(executor.map(lambda item: run_one(*item), work))

    actual_total = 0
    failed = 0
    summary_lines = []
    for index, seed, planned, executed, passed, log_path in sorted(results):
        actual_total += executed
        failed += 0 if passed else 1
        summary_lines.append(
            f"TASK index={index} seed={seed} planned={planned} executed={executed} "
            f"status={'PASS' if passed else 'FAIL'} log={log_path}"
        )

    overall = failed == 0 and actual_total == args.total
    summary_lines.append(
        f"SUMMARY fuType={args.fu_type} planned={args.total} executed={actual_total} "
        f"failed_tasks={failed} "
        f"status={'PASS' if overall else 'FAIL'}"
    )
    summary = "\n".join(summary_lines) + "\n"
    (args.output_dir / "summary.log").write_text(summary, encoding="utf-8")
    print(summary, end="")
    raise SystemExit(0 if overall else 1)


if __name__ == "__main__":
    main()
