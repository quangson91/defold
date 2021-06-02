#!/usr/bin/env python

""" Since github actions are quite difficult to use when writing scriptable things.
We rely on this script being able to output the unaltered text.
"""

import os, sys, platform

PLATFORMS_PRIVATE = ('x86_64-ps4', 'arm64-nx64')

# We use this function to determine if we should skip this platform
def can_build_private_platform(platform):
    return False

def repo_name_to_platforms(repository):
    return None

DIRNAME=os.path.dirname(os.path.abspath(__file__))
if os.path.exists(os.path.join(DIRNAME, 'ci_private.py')):
    import ci_private
    can_build_private_platform = ci_private.can_build_private_platform
    repo_name_to_platforms = ci_private.repo_name_to_platforms

def is_platform_private(platform):
    return platform in PLATFORMS_PRIVATE

def is_platform_supported(platform):
    if is_platform_private(platform):
        return can_build_private_platform(platform)
    return True

def is_platform_supported_by_repo(args):
    platform=args[0]
    if not is_platform_private(platform):
        # By default all public platforms are supported
        return True

    repository = os.environ.get('GITHUB_REPOSITORY', None)
    if repository is None:
        return True # probably a local build

    platforms = repo_name_to_platforms(repository)
    if platforms is not None and platform in platforms:
        return True

    return False

def is_repo_private():
    repository = os.environ.get('GITHUB_REPOSITORY', None)
    if repository is None:
        return False # probably a local build

    # get the platforms
    platforms = repo_name_to_platforms(repository)
    if platforms is not None:
        for platform in platforms:
            if is_platform_private(platform):
                return True
    return False


def should_build_on_private_repo(args):
    platform=args[0]
    if not is_platform_private(platform):
        # By default all public platforms are supported
        return False
    return True


def print_values(values):
    with sys.stdout as f:
        for i, value in enumerate(values):
            f.write("%s" % value)
            if i < len(values)-1:
                f.write(" ")

if __name__ == '__main__':
    command = sys.argv[1]
    if command == 'is_platform_supported_by_repo':
        result = is_platform_supported_by_repo(sys.argv[2:])
        print_values([result])
    elif command == 'should_build_on_private_repo':
        result = should_build_on_private_repo(sys.argv[2:])
        print_values([result])
