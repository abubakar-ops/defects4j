#-------------------------------------------------------------------------------
# Copyright (c) 2014-2017 René Just, Darioush Jalali, and Defects4J contributors.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#-------------------------------------------------------------------------------

=pod

=head1 NAME

Coverage.pm -- helper subroutines for code coverage analysis.

=head1 DESCRIPTION

This module provides helper subroutines for code coverage analysis using Cobertura.

=cut
# TODO: Clean up this module and provide a ".ser" parser for Cobertura's coverage results.
package Coverage;

use warnings;
use strict;

use Constants;
use Utils;
use DB;

# Cache column names for table coverage
my @COLS = DB::get_tab_columns($TAB_COVERAGE) or die "Cannot obtain table columns!";

# Default paths
my $SER_FILE = "cobertura.ser";
my $XML_FILE = "coverage.xml";

# Corbetura scripts
my $CORBETURA_MERGE  = "$SCRIPT_DIR/projects/lib/cobertura-merge.sh";
my $CORBETURA_REPORT = "$SCRIPT_DIR/projects/lib/cobertura-report.sh";

=pod

=head2 Static subroutines

  Coverage::coverage(project_ref, instrument_classes, src_dir, log_file, relevant_tests, [single_test, [merge_with]])

Measures code coverage for a provided L<Project> reference. F<instrument_classes>
is the name of a file that lists all the classes which should be instrumented.  F<src_dir>
provides the root directory of the source code, which is necessary to generate reports.

The test results are written to F<log_file>, and the boolean parameter C<relevant_tests>
indicates whether only relevant test cases are executed.

If C<single_test> is specified, only that test is run. This is meant to be used
in conjunction with C<merge_with>, which is the path to another .ser file obtained by
running coverage. This enables incremental analyses.

=cut
sub coverage {
	@_ >= 5 or die $ARG_ERROR;
	my ($project, $instrument_classes, $src_dir, $log_file, $relevant_tests, $single_test, $merge_with) = @_;

    my $root = $project->{prog_root};
	my $datafile = "$root/datafile";
	my $xmlfile  = "$root/$XML_FILE";
	my $serfile  = "$root/$SER_FILE";

    # Remove stale data file
    system("rm -f $serfile");

    # Instrument all classes provided
	$project->coverage_instrument($instrument_classes) or return undef;

    # Execute test suite
    if ($relevant_tests) {
        $project->run_relevant_tests($log_file) or return undef;
    } else {
        $project->run_tests($log_file, $single_test) or return undef;
    }

	# Generate coverage report
	my $result_xml;
	if (defined $merge_with) {
		print(STDERR "Merging & creating new report via shell script..\n");

		# Remove stale data files
		system("rm -f $datafile") if -e $datafile;
		system("rm -f $xmlfile")  if -e $xmlfile ;

		system("sh $CORBETURA_MERGE --datafile $datafile $merge_with $serfile >/dev/null 2>&1") == 0 or die "could not merge results";
		system("sh $CORBETURA_REPORT --format xml --datafile $datafile --destination $root >/dev/null 2>&1") == 0 or die "could not create report";

	} else {
		# Generate XML directly if merge is not needed.
		$project->coverage_report($src_dir) or die "Could not create coverage report";
	}

	return  _get_info_from_xml($xmlfile);
}

=pod

  Coverage::coverage_ext(project, instrument_classes, src_dir, test_dir, include_pattern, log_file)

Determines code coverage for an external test suite.
F<instrument_classes> is the name of a file that lists all the classes which
should be instrumented.  C<src_dir> provides the root directory of the source
code, which is necessary to generate reports.

=cut
sub coverage_ext {
	@_ == 6 or die $ARG_ERROR;
	my ($project, $instrument_classes, $src_dir, $test_dir, $include, $log_file) = @_;

    # Instrument all classes provided
	$project->coverage_instrument($instrument_classes) or return undef;

    # Execute test suite
	$project->run_ext_tests($test_dir, $include, $log_file) or die "Could not run test suite";

    # Generate coverage report
	$project->coverage_report($src_dir) or die "Could not create report";

    # Parse xml output and return coverage ratios
	my $xmlfile  = "$project->{prog_root}/$XML_FILE";
	return _get_info_from_xml($xmlfile);
}

=pod

  Coverage::insert_row(hashref, [out_dir])

Insert a row into the database table L<TAB_COVERAGE|DB>. C<hashref> points to a
hash holding all key-value pairs of the data row.  F<out_dir> is the optional
alternative database directory to use.

=cut
sub insert_row {
    my ($data, $out_dir) = @_;

	# Get proper output db handle: check whether a different output directory is provided
    my $dbh;
    if (defined $out_dir) {
        $dbh = DB::get_db_handle($TAB_COVERAGE, $out_dir);
    } else {
        $dbh = DB::get_db_handle($TAB_COVERAGE);
    }

    my @tmp;
    foreach (@COLS) {
        push (@tmp, $dbh->quote((defined $data->{$_} ? $data->{$_} : "-")));
    }

    my $row = join(",", @tmp);
    $dbh->do("INSERT INTO $TAB_COVERAGE VALUES ($row)");
}

=pod

  Coverage::copy_coverage_logs(project, vid, suite, test_id, log_dir)

Copies the coverage log files to a permanent directory F<log_dir>.  C<project>
is the reference to a L<Project>, C<vid> is the version id, C<suite> specifies
the suite tag (e.g., manual, randoop, evosuite-branch), and C<test_id> provides
the id of the test suite.

=cut
sub copy_coverage_logs {
    my ($project, $vid, $suite, $test_id, $log_dir) = @_;

	# Copy coverage log files to log directory
    system("cp $project->{prog_root}/$SER_FILE $log_dir/$suite/$vid.$test_id.ser") == 0
        or die "Cannot copy .ser file";
	system("cp $project->{prog_root}/$XML_FILE $log_dir/$suite/$vid.$test_id.xml") == 0
		or die "Cannot copy .xml file";
}

=pod

=over 4

=item B<parse_xml_log> C<parse_xml_log(project_id, version_id, suite_src, test_id, db_dir)>

Parses the coverage log file (xml) and returns a reference to a hash that 
provides all covered and uncovered line numbers for all covered files -- returns 
undef if the log file does not exist. The data structure of the returned hash 
reference looks like:

{org/foo/Class1.java} => {covered => [1 2 3 6 7 8], uncovered => [4 5]}

{org/foo/Class2.java} => {covered => [1 2], uncovered => [3 4]}

=back

=cut
sub parse_xml_log {
    my ($pid, $vid, $suite_src, $tid, $db_dir) = @_;
    my $xml = "$db_dir/coverage_log/$pid/$suite_src/$vid.$tid.xml";
    -e $xml or return undef;

    # Parse XML file
    open(IN, $xml) or die "Cannot read coverage log file: $!";
    my $file;
    my %coverage;
    my $hit="covered";
    my $miss="uncovered";
    while (<IN>) {
        /^\s*<class name=\"([^"]+)\" filename=\"([^"]+)\" line-rate=.*$/ and $file = $2;
        if (/^\s*<line number=\"(\d+)\" hits=\"(\d+)\" branch=\"(true|false)\".*$/) {
            defined $file or die "Name of covered class unknown!";
            my $line=$1;
            my $type=($2==0 ? $miss : $hit);

            unless (defined $coverage{$file}) {
                $coverage{$file} = {};
            }
            unless (defined $coverage{$file}->{$type}) {
                $coverage{$file}->{$type} = {};
            }
            $coverage{$file}->{$type}->{$line} = 1;
        }
    }
    close IN;

    return \%coverage;
}

#
# Parse coverage log file and return reference to a hash that holds all results
#
sub _get_info_from_xml {
    my ($xml, ) = @_;
    my ($lt, $lc, $bt, $bc);

    -e $xml or die "Result xml file does not exist: $xml!";

    # Parse XML file
    open FH, $xml;
    while (<FH>) {
        if (/lines-covered="(\d+)" lines-valid="(\d+)" branches-covered="(\d+)" branches-valid="(\d+)"/) {
            ($lc, $lt, $bc, $bt) = ($1, $2, $3, $4);
        }
    }
    close FH;

    die "values not set" unless defined $lt;

    # Set all values and return hash reference
    return {
        $LINES_TOTAL => $lt,
        $LINES_COVERED => $lc,
        $BRANCHES_TOTAL => $bt,
        $BRANCHES_COVERED => $bc,
    };
}

1;
